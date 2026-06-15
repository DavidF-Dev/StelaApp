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
import dev.davidfdev.stela.settings.NoteFilter
import dev.davidfdev.stela.settings.SettingsRepository
import dev.davidfdev.stela.settings.SortOrder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/// One-shot events the list surfaces to the UI (which can't be modelled as state).
sealed interface NoteListEvent {
    /// [count] notes were just deleted; the UI offers an undo.
    data class NotesDeleted(val count: Int) : NoteListEvent

    /// [count] notes were just archived; the UI offers an undo.
    data class NotesArchived(val count: Int) : NoteListEvent
}

data class NoteListUiState(
    val notes: List<Note> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.MODIFIED,
    val sortReversed: Boolean = false,
    val noteFilter: NoteFilter = NoteFilter.ALL,
    val isSourceEmpty: Boolean = true,
) {
    val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val selectedCount: Int get() = selectedIds.size

    /// True when the batch toggle should pin (at least one selected note is unpinned);
    /// false when every selected note is already pinned and the toggle should unpin.
    val batchActionPins: Boolean get() = notes.any { it.id in selectedIds && !it.isPinned }

    /// True when every visible note is selected, so the select-all toggle should clear.
    val allSelected: Boolean get() = notes.isNotEmpty() && notes.all { it.id in selectedIds }
}

class NoteListViewModel(
    repository: NoteRepository,
    private val pinner: NotePinner,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    // Transient: search resets each session, unlike the persisted sort/filter.
    private val searchQuery = MutableStateFlow("")

    private val eventsChannel = Channel<NoteListEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    // The last batch deleted, held so an Undo can restore it.
    private var recentlyDeleted: List<Note> = emptyList()
    // The last batch archived (pre-archive snapshots), held so an Undo can restore pin state too.
    private var recentlyArchived: List<Note> = emptyList()

    val uiState: StateFlow<NoteListUiState> =
        combine(repository.notes, selectedIds, searchQuery, settingsRepository.settings) { source, selected, search, settings ->
            // Drop ids whose note no longer exists so selection can't outlive a delete.
            val present = selected.filterTo(mutableSetOf()) { id -> source.any { it.id == id } }
            NoteListUiState(
                notes = applyQuery(source, search, settings.sortOrder, settings.noteFilter, settings.sortReversed),
                selectedIds = present,
                searchQuery = search,
                sortOrder = settings.sortOrder,
                sortReversed = settings.sortReversed,
                noteFilter = settings.noteFilter,
                // Onboarding empties on no active notes — archived-only still shows "no notes yet".
                isSourceEmpty = source.none { !it.isArchived },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = NoteListUiState(),
        )

    fun onSearchChange(query: String) {
        searchQuery.value = query
    }

    fun onSortChange(order: SortOrder) {
        viewModelScope.launch { settingsRepository.setSortOrder(order) }
    }

    fun onToggleSortDirection() {
        val reversed = uiState.value.sortReversed
        viewModelScope.launch { settingsRepository.setSortReversed(!reversed) }
    }

    fun onFilterChange(filter: NoteFilter) {
        viewModelScope.launch { settingsRepository.setNoteFilter(filter) }
    }

    fun pin(note: Note) {
        // A single-note pin from the list is a genuine, attended transition — alert if the note opted in
        // (the batch toggle stays silent).
        viewModelScope.launch { pinner.pin(note, alert = true) }
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

    /// Selects every visible note, or clears the selection when all are already selected
    /// (which exits selection mode). Operates on the visible list, so an active filter
    /// scopes it to what the user can see.
    fun toggleSelectAll() {
        val visibleIds = uiState.value.notes.mapTo(mutableSetOf()) { it.id }
        selectedIds.value = if (visibleIds.isNotEmpty() && selectedIds.value.containsAll(visibleIds)) {
            emptySet()
        } else {
            visibleIds
        }
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

    /// Archives every selected note (the reversible alternative to delete), then exits
    /// selection mode and offers an undo. They move to the archived destination, not the trash.
    fun batchArchive() {
        val archived = selectedNotes()
        viewModelScope.launch {
            pinner.archiveAll(archived)
            recentlyArchived = archived
            clearSelection()
            eventsChannel.send(NoteListEvent.NotesArchived(archived.size))
        }
    }

    /// Restores the most recently archived batch to its prior state: unarchives every note and
    /// re-pins the ones that were pinned before archiving (archiving had unpinned them).
    fun undoArchive() {
        val toRestore = recentlyArchived
        recentlyArchived = emptyList()
        if (toRestore.isEmpty()) return
        viewModelScope.launch {
            pinner.unarchiveAll(toRestore)
            val wasPinned = toRestore.filter { it.isPinned }
            if (wasPinned.isNotEmpty()) pinner.pinAll(wasPinned)
        }
    }

    fun batchDelete() {
        val deleted = selectedNotes()
        viewModelScope.launch {
            pinner.deleteAll(deleted)
            recentlyDeleted = deleted
            clearSelection()
            eventsChannel.send(NoteListEvent.NotesDeleted(deleted.size))
        }
    }

    /// Restores the most recently deleted batch (re-pinning the ones that were pinned).
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
                NoteListViewModel(
                    app.container.noteRepository,
                    app.container.notePinner,
                    app.container.settingsRepository,
                )
            }
        }
    }
}
