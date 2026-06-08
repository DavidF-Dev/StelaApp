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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val title: String = "",
    val description: String = "",
    val isEditing: Boolean = false,
    val isPinned: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank()
}

class EditorViewModel(
    private val repository: NoteRepository,
    private val pinner: NotePinner,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val noteId: Long? = savedStateHandle[NOTE_ID_KEY]

    private val _uiState = MutableStateFlow(EditorUiState(isEditing = noteId != null))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // Retained so a save preserves fields the editor doesn't expose (createdAt, iconId, isPinned).
    private var loaded: Note? = null

    init {
        if (noteId != null) {
            viewModelScope.launch {
                repository.getById(noteId)?.let { note ->
                    loaded = note
                    _uiState.update {
                        it.copy(title = note.title, description = note.description, isPinned = note.isPinned)
                    }
                }
            }
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value) }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }

    fun pin() {
        val note = loaded ?: return
        viewModelScope.launch {
            pinner.pin(note)
            loaded = note.copy(isPinned = true)
            _uiState.update { it.copy(isPinned = true) }
        }
    }

    fun unpin() {
        val note = loaded ?: return
        viewModelScope.launch {
            pinner.unpin(note.id)
            loaded = note.copy(isPinned = false)
            _uiState.update { it.copy(isPinned = false) }
        }
    }

    fun save(onComplete: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            val existing = loaded
            if (existing == null) {
                repository.create(state.title, state.description)
            } else {
                val updated = existing.copy(title = state.title, description = state.description)
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
            repository.delete(existing)
            onComplete()
        }
    }

    companion object {
        const val NOTE_ID_KEY = "noteId"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StelaApp
                EditorViewModel(app.container.noteRepository, app.container.notePinner, createSavedStateHandle())
            }
        }
    }
}
