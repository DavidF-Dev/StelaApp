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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteListUiState(
    val notes: List<Note> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
) {
    val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val selectedCount: Int get() = selectedIds.size

    /// True when the batch toggle should pin (at least one selected note is unpinned);
    /// false when every selected note is already pinned and the toggle should unpin.
    val batchActionPins: Boolean get() = notes.any { it.id in selectedIds && !it.isPinned }
}

class NoteListViewModel(
    repository: NoteRepository,
    private val pinner: NotePinner,
) : ViewModel() {

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<NoteListUiState> =
        combine(repository.notes, selectedIds) { notes, selected ->
            // Drop ids whose note no longer exists so selection can't outlive a delete.
            val present = selected.filterTo(mutableSetOf()) { id -> notes.any { it.id == id } }
            NoteListUiState(notes = notes, selectedIds = present)
        }.stateIn(
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

    fun toggleSelection(id: Long) {
        selectedIds.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    /// Pins every selected-unpinned note, or unpins all when every selected note is
    /// already pinned, then exits selection mode.
    fun batchTogglePin() {
        val pins = uiState.value.batchActionPins
        val selected = selectedNotes()
        viewModelScope.launch {
            if (pins) {
                pinner.pinAll(selected.filter { !it.isPinned })
            } else {
                pinner.unpinAll(selected.map { it.id })
            }
            clearSelection()
        }
    }

    fun batchDelete() {
        val selected = selectedNotes()
        viewModelScope.launch {
            pinner.deleteAll(selected)
            clearSelection()
        }
    }

    private fun selectedNotes(): List<Note> =
        uiState.value.let { state -> state.notes.filter { it.id in state.selectedIds } }

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
