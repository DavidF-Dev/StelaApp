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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val advancedExpanded: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank()

    // A new note is "loaded" immediately; an existing note only once its row has been read (createdAt
    // set). Gates the title auto-focus so an async load doesn't momentarily look blank.
    val noteLoaded: Boolean get() = !isEditing || createdAt != null
}

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

    init {
        if (noteId != null) {
            viewModelScope.launch {
                repository.getById(noteId)?.let { note ->
                    loaded = note
                    _uiState.update {
                        it.copy(
                            // A draft overlays its unsaved field edits on top of the stored note.
                            title = draft?.title ?: note.title,
                            description = draft?.description ?: note.description,
                            emoji = draft?.emoji ?: note.emoji,
                            isPinned = note.isPinned,
                            isArchived = note.isArchived,
                            createdAt = note.createdAt,
                            updatedAt = note.updatedAt,
                            pinAt = note.pinAt,
                            unpinAt = note.unpinAt,
                        )
                    }
                }
            }
        } else if (draft != null) {
            // New, unsaved note expanded from the popup: seed the in-progress fields (it stays a create).
            _uiState.update {
                it.copy(title = draft.title, description = draft.description, emoji = draft.emoji)
            }
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value) }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }

    fun onEmojiChange(value: String) = _uiState.update { it.copy(emoji = value) }

    fun onPinAtChange(value: Long?) = _uiState.update { it.copy(pinAt = value) }

    fun onUnpinAtChange(value: Long?) = _uiState.update { it.copy(unpinAt = value) }

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
            // reflect all three so the Advanced "Pin at" row matches what the pinner persisted.
            pinner.pin(note)
            loaded = note.copy(isPinned = true, isArchived = false, pinAt = null)
            _uiState.update { it.copy(isPinned = true, isArchived = false, pinAt = null) }
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
            _uiState.update { it.copy(isPinned = false, unpinAt = null) }
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
            _uiState.update { it.copy(isPinned = false, pinAt = untilMillis) }
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
                val id = repository.create(title, state.description, emoji = emoji)
                // Mirror the other pin entry points: only pin when notifications can post.
                if (state.isPinned && canPostNotifications()) repository.getById(id)?.let { pinner.pin(it) }
                pinner.applySchedule(id, state.pinAt, state.unpinAt)
            } else {
                val updated = existing.copy(
                    title = title,
                    description = state.description,
                    emoji = emoji,
                )
                repository.update(updated)
                pinner.refresh(updated)
                pinner.applySchedule(existing.id, state.pinAt, state.unpinAt)
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
            pinner.delete(existing)
            onComplete()
        }
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
