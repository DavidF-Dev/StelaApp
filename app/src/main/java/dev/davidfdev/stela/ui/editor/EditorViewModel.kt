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
) {
    val canSave: Boolean get() = title.isNotBlank()
}

class EditorViewModel(
    private val repository: NoteRepository,
    private val pinner: NotePinner,
    savedStateHandle: SavedStateHandle,
    private val canPostNotifications: () -> Boolean = { true },
) : ViewModel() {

    private val noteId: Long? = savedStateHandle[NOTE_ID_KEY]

    // Seeds a new note's pin toggle (its intended state until saved); new-note routes default it to true.
    private val initialPinned: Boolean = savedStateHandle[PIN_KEY] ?: true

    private val _uiState = MutableStateFlow(
        EditorUiState(isEditing = noteId != null, isPinned = noteId == null && initialPinned),
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
                            title = note.title,
                            description = note.description,
                            emoji = note.emoji,
                            isPinned = note.isPinned,
                            isArchived = note.isArchived,
                            createdAt = note.createdAt,
                            updatedAt = note.updatedAt,
                        )
                    }
                }
            }
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value) }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }

    fun onEmojiChange(value: String) = _uiState.update { it.copy(emoji = value) }

    fun pin() {
        // New note (not yet persisted): record the intent; it is pinned on save, not live.
        val note = loaded ?: run {
            _uiState.update { it.copy(isPinned = true) }
            return
        }
        viewModelScope.launch {
            // Pinning an archived note restores it first; reflect both flags.
            pinner.pin(note)
            loaded = note.copy(isPinned = true, isArchived = false)
            _uiState.update { it.copy(isPinned = true, isArchived = false) }
        }
    }

    fun unpin() {
        val note = loaded ?: run {
            _uiState.update { it.copy(isPinned = false) }
            return
        }
        viewModelScope.launch {
            pinner.unpin(note.id)
            loaded = note.copy(isPinned = false)
            _uiState.update { it.copy(isPinned = false) }
        }
    }

    /// Archives an existing note (reversible; unpins it). A no-op for an unsaved note,
    /// mirroring delete.
    fun archive() {
        val note = loaded ?: return
        viewModelScope.launch {
            pinner.archive(note)
            loaded = note.copy(isArchived = true, isPinned = false)
            _uiState.update { it.copy(isArchived = true, isPinned = false) }
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
            val existing = loaded
            if (existing == null) {
                val id = repository.create(state.title, state.description, emoji = state.emoji)
                // Mirror the other pin entry points: only pin when notifications can post.
                if (state.isPinned && canPostNotifications()) repository.getById(id)?.let { pinner.pin(it) }
            } else {
                val updated = existing.copy(
                    title = state.title,
                    description = state.description,
                    emoji = state.emoji,
                )
                repository.update(updated)
                pinner.refresh(updated)
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
                EditorViewModel(
                    app.container.noteRepository,
                    app.container.notePinner,
                    createSavedStateHandle(),
                ) { canPostNotifications(app) }
            }
        }
    }
}
