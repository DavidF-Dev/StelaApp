package dev.davidfdev.stela.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.davidfdev.stela.StelaApp
import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.data.emojiRangesOf
import dev.davidfdev.stela.data.promoteLeadingEmoji
import dev.davidfdev.stela.pin.NotePinner
import dev.davidfdev.stela.ui.canPostNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/// The persisted baseline of an existing note's editable fields, against which [EditorUiState.isDirty]
/// is measured. Null for a new, never-saved note.
data class EditorSnapshot(
    val title: String,
    val description: String,
    val emoji: String,
    val pinAt: Long?,
    val unpinAt: Long?,
    val alertOnPin: Boolean,
)

data class EditorUiState(
    val title: String = "",
    val description: String = "",
    val emoji: String = "",
    val isEditing: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val pinAt: Long? = null,
    val unpinAt: Long? = null,
    val alertOnPin: Boolean = false,
    val advancedExpanded: Boolean = false,
    val savedSnapshot: EditorSnapshot? = null,
) {
    val canSave: Boolean get() = title.isNotBlank()

    // A new note is "loaded" immediately; an existing note only once its row has been read (createdAt
    // set). Gates the title auto-focus so an async load doesn't momentarily look blank.
    val noteLoaded: Boolean get() = !isEditing || createdAt != null

    /// Whether the editable fields differ from what's persisted — drives the back-to-discard confirm.
    /// Pin/archive state is excluded: it persists immediately, so it is never an unsaved edit. A new note
    /// (null snapshot) is dirty once it holds any content or a schedule.
    val isDirty: Boolean
        get() = savedSnapshot.let { snapshot ->
            if (snapshot == null) {
                title.isNotBlank() || description.isNotBlank() || emoji.isNotBlank() ||
                    pinAt != null || unpinAt != null || alertOnPin
            } else {
                title != snapshot.title || description != snapshot.description || emoji != snapshot.emoji ||
                    pinAt != snapshot.pinAt || unpinAt != snapshot.unpinAt || alertOnPin != snapshot.alertOnPin
            }
        }
}

private fun Note.snapshot() = EditorSnapshot(title, description, emoji, pinAt, unpinAt, alertOnPin)

class EditorViewModel(
    private val repository: NoteRepository,
    private val pinner: NotePinner,
    savedStateHandle: SavedStateHandle,
    draft: NoteDraft? = null,
    private val canPostNotifications: () -> Boolean = { true },
    initialAdvancedExpanded: Boolean = false,
    private val onAdvancedExpandedChange: (Boolean) -> Unit = {},
    // Injected so the save-time emoji detection (which needs EmojiManager) can be faked in JVM tests.
    private val detectEmojiRanges: (String) -> List<IntRange> = ::emojiRangesOf,
) : ViewModel() {

    // A draft (Expand from the popup) carries the note id; otherwise it comes from the edit route.
    private val noteId: Long? = draft?.noteId ?: savedStateHandle[NOTE_ID_KEY]

    // Seeds a new note's pin toggle (its intended state until saved); new-note routes default it to true.
    private val initialPinned: Boolean = draft?.pinOnSave ?: savedStateHandle[PIN_KEY] ?: true

    private val _uiState = MutableStateFlow(
        EditorUiState(
            isEditing = noteId != null,
            isPinned = noteId == null && initialPinned,
            advancedExpanded = initialAdvancedExpanded,
        ),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // Retained so a save preserves fields the editor doesn't expose (createdAt, iconId, isPinned).
    private var loaded: Note? = null

    // Set true when the observed note vanishes after having loaded — i.e. it was deleted out from under
    // the editor; the screen watches this and finishes itself.
    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    // Set when this editor deletes its own note, so the resulting null emission isn't mistaken for an
    // external deletion (which would double-fire navigation).
    private var selfDeleted = false

    init {
        if (noteId != null) {
            // Observe the row (not a one-shot read) so a change made elsewhere — a background auto-pin /
            // auto-unpin, a snooze, or a notification action — stays reflected here and can't be silently
            // overwritten on save.
            viewModelScope.launch {
                repository.observeById(noteId).collect { note ->
                    when {
                        note == null -> {
                            if (loaded != null && !selfDeleted) _closed.value = true
                            return@collect
                        }
                        loaded == null -> seedFrom(note, draft)
                        else -> refreshAuthoritativeFields(note)
                    }
                    loaded = note
                }
            }
        } else if (draft != null) {
            // New, unsaved note expanded from the popup: seed the in-progress fields (it stays a create).
            _uiState.update {
                it.copy(title = draft.title, description = draft.description, emoji = draft.emoji)
            }
        }
    }

    /// Seeds the editor from the first load of an existing note. A [draft] (carried in from the popup's
    /// Expand) overlays its unsaved field edits on top of the stored note, which stays the dirty baseline.
    private fun seedFrom(note: Note, draft: NoteDraft?) {
        _uiState.update {
            it.copy(
                title = draft?.title ?: note.title,
                description = draft?.description ?: note.description,
                emoji = draft?.emoji ?: note.emoji,
                isPinned = note.isPinned,
                isArchived = note.isArchived,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                pinAt = note.pinAt,
                unpinAt = note.unpinAt,
                alertOnPin = note.alertOnPin,
                savedSnapshot = note.snapshot(),
            )
        }
    }

    /// Folds an external change (a background auto-pin/unpin, a snooze, or an archive from a notification
    /// action) into the editor without disturbing the user's in-progress text or a pending schedule edit.
    /// Pin/archive state and the modified time are authoritative from the store. `pinAt`/`unpinAt` are
    /// unsaved-editable, so they are adopted only when the user hasn't edited them — or when the new pin
    /// state disables their row (a now-unreachable pending edit is dropped rather than left dangling). The
    /// dirty baseline always tracks the stored schedule, so a preserved edit still reads dirty.
    private fun refreshAuthoritativeFields(note: Note) {
        _uiState.update { state ->
            val pinAtClean = state.pinAt == state.savedSnapshot?.pinAt
            // "Pin at" is disabled once the note is pinned, so drop any pending edit it can no longer show.
            val newPinAt = if (pinAtClean || note.isPinned) note.pinAt else state.pinAt
            val unpinAtClean = state.unpinAt == state.savedSnapshot?.unpinAt
            // "Unpin at" needs a pin (live or scheduled) to act on; drop a pending edit its row can't show.
            val unpinApplicable = note.isPinned || newPinAt != null
            val newUnpinAt = if (unpinAtClean || !unpinApplicable) note.unpinAt else state.unpinAt
            state.copy(
                isPinned = note.isPinned,
                isArchived = note.isArchived,
                updatedAt = note.updatedAt,
                pinAt = newPinAt,
                unpinAt = newUnpinAt,
                savedSnapshot = state.savedSnapshot?.copy(pinAt = note.pinAt, unpinAt = note.unpinAt),
            )
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value) }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }

    fun onEmojiChange(value: String) = _uiState.update { it.copy(emoji = value) }

    fun onPinAtChange(value: Long?) = _uiState.update { it.copy(pinAt = value) }

    fun onUnpinAtChange(value: Long?) = _uiState.update { it.copy(unpinAt = value) }

    fun onAlertOnPinChange(value: Boolean) = _uiState.update { it.copy(alertOnPin = value) }

    /// Expands or collapses the editor's Advanced section, remembering the choice process-wide (so it
    /// survives reopening the editor, but not a cold start).
    fun setAdvancedExpanded(expanded: Boolean) {
        _uiState.update { it.copy(advancedExpanded = expanded) }
        onAdvancedExpandedChange(expanded)
    }

    fun pin() {
        // New note (not yet persisted): record the intent; it is pinned on save, not live.
        val note = loaded ?: run {
            _uiState.update { it.copy(isPinned = true) }
            return
        }
        viewModelScope.launch {
            // Pinning an archived note restores it first, and clears any pending auto-pin (now fulfilled);
            // reflect all three so the Advanced "Pin at" row matches what the pinner persisted. A manual pin
            // is a genuine transition — alert if the note opted in.
            pinner.pin(note, alert = true)
            loaded = note.copy(isPinned = true, isArchived = false, pinAt = null)
            // pinAt is now persisted as null, so fold it into the baseline lest it read as a dirty edit.
            _uiState.update {
                it.copy(
                    isPinned = true,
                    isArchived = false,
                    pinAt = null,
                    savedSnapshot = it.savedSnapshot?.copy(pinAt = null),
                )
            }
        }
    }

    fun unpin() {
        val note = loaded ?: run {
            _uiState.update { it.copy(isPinned = false) }
            return
        }
        viewModelScope.launch {
            // Unpinning clears any pending auto-unpin (now fulfilled); reflect it in the Advanced row.
            pinner.unpin(note.id)
            loaded = note.copy(isPinned = false, unpinAt = null)
            _uiState.update {
                it.copy(isPinned = false, unpinAt = null, savedSnapshot = it.savedSnapshot?.copy(unpinAt = null))
            }
        }
    }

    /// Archives an existing note (reversible; unpins it). A no-op for an unsaved note,
    /// mirroring delete. [onComplete] runs once the archive has persisted, so a caller that
    /// finishes its host afterwards doesn't cancel the work mid-flight.
    fun archive(onComplete: () -> Unit = {}) {
        val note = loaded ?: run { onComplete(); return }
        viewModelScope.launch {
            pinner.archive(note)
            loaded = note.copy(isArchived = true, isPinned = false)
            _uiState.update { it.copy(isArchived = true, isPinned = false) }
            onComplete()
        }
    }

    /// Snoozes an existing note: hides it now and re-pins it at [untilMillis] (reusing `pinAt`). Keeps any
    /// auto-unpin window. A no-op for an unsaved note (the action is disabled until it's pinned).
    fun snooze(untilMillis: Long) {
        val note = loaded ?: return
        viewModelScope.launch {
            pinner.snooze(note.id, untilMillis)
            loaded = note.copy(isPinned = false, pinAt = untilMillis)
            _uiState.update {
                it.copy(isPinned = false, pinAt = untilMillis, savedSnapshot = it.savedSnapshot?.copy(pinAt = untilMillis))
            }
        }
    }

    /// Restores an existing note from the archive.
    fun unarchive() {
        val note = loaded ?: return
        viewModelScope.launch {
            pinner.unarchive(note)
            loaded = note.copy(isArchived = false)
            _uiState.update { it.copy(isArchived = false) }
        }
    }

    fun save(onComplete: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            // Quality-of-life: a title that leads with an emoji (and no emoji chosen) promotes it into the
            // emoji slot. Display is unchanged; the inline emoji just becomes the structured one.
            val promotion = promoteLeadingEmoji(state.title, state.emoji, detectEmojiRanges(state.title))
            val title = promotion?.title ?: state.title
            val emoji = promotion?.emoji ?: state.emoji
            val existing = loaded
            if (existing == null) {
                val id = repository.create(title, state.description, emoji = emoji, alertOnPin = state.alertOnPin)
                // Mirror the other pin entry points: only pin when notifications can post. A pin-on-save is a
                // genuine transition — alert if the note opted in.
                if (state.isPinned && canPostNotifications()) repository.getById(id)?.let { pinner.pin(it, alert = true) }
                pinner.applySchedule(id, state.pinAt, state.unpinAt)
            } else {
                // Persist only what actually changed: a content edit bumps updatedAt (and refreshes the
                // live notification); a schedule or alert-flag edit doesn't bump it; a save with no real
                // change writes nothing, so the modified time stays put.
                val contentChanged = title != existing.title ||
                    state.description != existing.description ||
                    emoji != existing.emoji
                val scheduleChanged = state.pinAt != existing.pinAt || state.unpinAt != existing.unpinAt
                val alertChanged = state.alertOnPin != existing.alertOnPin
                if (contentChanged) {
                    // Field-scoped write: never round-trips pin/schedule fields, so a content save can't
                    // revert a background change (the row may have auto-pinned while the editor was open).
                    repository.updateContent(existing.id, title, state.description, emoji)
                    val refreshed = existing.copy(title = title, description = state.description, emoji = emoji)
                    pinner.refresh(refreshed)
                    loaded = refreshed
                }
                if (scheduleChanged) {
                    pinner.applySchedule(existing.id, state.pinAt, state.unpinAt)
                }
                if (alertChanged) {
                    repository.setAlertOnPin(existing.id, state.alertOnPin)
                    loaded = loaded?.copy(alertOnPin = state.alertOnPin)
                }
            }
            onComplete()
        }
    }

    fun delete(onComplete: () -> Unit) {
        val existing = loaded
        if (existing == null) {
            onComplete()
            return
        }
        viewModelScope.launch {
            // Mark before deleting so the observer's resulting null emission isn't read as an external
            // deletion (which would fire the close signal on top of this navigation).
            selfDeleted = true
            pinner.delete(existing)
            onComplete()
        }
    }

    // The most recent duplicate, retained so [undoDuplicate] can remove it.
    private var lastDuplicateId: Long? = null

    /// Creates an independent copy of the current fields — fresh id, unpinned, unscheduled, active, fresh
    /// timestamps (all from [NoteRepository.create]) — leaving the edited note untouched. [onDuplicated]
    /// runs after it lands, so the caller can offer Undo.
    fun duplicate(onDuplicated: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val promotion = promoteLeadingEmoji(state.title, state.emoji, detectEmojiRanges(state.title))
            val title = promotion?.title ?: state.title
            val emoji = promotion?.emoji ?: state.emoji
            lastDuplicateId = repository.create(title, state.description, emoji = emoji)
            onDuplicated()
        }
    }

    /// Removes the most recent duplicate (the Undo for [duplicate]).
    fun undoDuplicate() {
        val id = lastDuplicateId ?: return
        lastDuplicateId = null
        viewModelScope.launch { repository.getById(id)?.let { pinner.delete(it) } }
    }

    companion object {
        const val NOTE_ID_KEY = "noteId"
        const val PIN_KEY = "pin"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StelaApp
                // Consume the one-shot Expand hand-off (if any) so it seeds this editor exactly once.
                val draft = app.container.pendingDraft
                app.container.pendingDraft = null
                EditorViewModel(
                    app.container.noteRepository,
                    app.container.notePinner,
                    createSavedStateHandle(),
                    draft,
                    canPostNotifications = { canPostNotifications(app) },
                    initialAdvancedExpanded = app.container.editorAdvancedExpanded,
                    onAdvancedExpandedChange = { app.container.editorAdvancedExpanded = it },
                )
            }
        }
    }
}
