package dev.davidfdev.stela.ui.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.davidfdev.stela.StelaApp
import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.pin.NotePinner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NoteListUiState(val notes: List<Note> = emptyList())

class NoteListViewModel(
    repository: NoteRepository,
    private val pinner: NotePinner,
) : ViewModel() {

    val uiState: StateFlow<NoteListUiState> =
        repository.notes
            .map { NoteListUiState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = NoteListUiState(),
            )

    fun pin(note: Note) {
        viewModelScope.launch { pinner.pin(note) }
    }

    fun unpin(note: Note) {
        viewModelScope.launch { pinner.unpin(note.id) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StelaApp
                NoteListViewModel(app.container.noteRepository, app.container.notePinner)
            }
        }
    }
}
