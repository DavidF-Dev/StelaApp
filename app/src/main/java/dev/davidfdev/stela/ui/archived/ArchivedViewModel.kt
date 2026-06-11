package dev.davidfdev.stela.ui.archived

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.davidfdev.stela.StelaApp
import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.pin.NotePinner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/// One-shot events the archived screen surfaces to the UI.
sealed interface ArchivedEvent {
    /// [count] notes were just deleted; the UI offers an undo.
    data class NotesDeleted(val count: Int) : ArchivedEvent
}

data class ArchivedUiState(
    val notes: List<Note> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
) {
    val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val selectedCount: Int get() = selectedIds.size
    val allSelected: Boolean get() = notes.isNotEmpty() && notes.all { it.id in selectedIds }
}

class ArchivedViewModel(
    repository: NoteRepository,
    private val pinner: NotePinner,
) : ViewModel() {

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    private val eventsChannel = Channel<ArchivedEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private var recentlyDeleted: List<Note> = emptyList()

    val uiState: StateFlow<ArchivedUiState> =
        combine(repository.notes, selectedIds) { source, selected ->
            val archived = source.filter { it.isArchived }.sortedByDescending { it.updatedAt }
            // Drop ids whose note no longer shows here (restored or deleted) so selection can't outlive it.
            val present = selected.filterTo(mutableSetOf()) { id -> archived.any { it.id == id } }
            ArchivedUiState(notes = archived, selectedIds = present)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ArchivedUiState(),
        )

    fun toggleSelection(id: Long) {
        selectedIds.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    /// Selects every archived note, or clears when all are already selected (exiting selection mode).
    fun toggleSelectAll() {
        val visibleIds = uiState.value.notes.mapTo(mutableSetOf()) { it.id }
        selectedIds.value = if (visibleIds.isNotEmpty() && selectedIds.value.containsAll(visibleIds)) {
            emptySet()
        } else {
            visibleIds
        }
    }

    /// Restores every selected note from the archive, then exits selection mode.
    fun restoreSelected() {
        val selected = selectedNotes()
        viewModelScope.launch {
            pinner.unarchiveAll(selected)
            clearSelection()
        }
    }

    fun deleteSelected() {
        val deleted = selectedNotes()
        viewModelScope.launch {
            pinner.deleteAll(deleted)
            recentlyDeleted = deleted
            clearSelection()
            eventsChannel.send(ArchivedEvent.NotesDeleted(deleted.size))
        }
    }

    /// Restores the most recently deleted batch. Re-insertion preserves the archive flag, so
    /// the notes return to this screen rather than the main list.
    fun undoDelete() {
        val toRestore = recentlyDeleted
        recentlyDeleted = emptyList()
        if (toRestore.isNotEmpty()) {
            viewModelScope.launch { pinner.restore(toRestore) }
        }
    }

    private fun selectedNotes(): List<Note> =
        uiState.value.let { state -> state.notes.filter { it.id in state.selectedIds } }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StelaApp
                ArchivedViewModel(
                    app.container.noteRepository,
                    app.container.notePinner,
                )
            }
        }
    }
}
