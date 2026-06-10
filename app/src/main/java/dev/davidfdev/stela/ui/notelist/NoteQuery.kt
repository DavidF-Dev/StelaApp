package dev.davidfdev.stela.ui.notelist

import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.settings.NoteFilter
import dev.davidfdev.stela.settings.SortOrder

/// Derives the displayed note list in one in-memory pass: filter by pin state, keep
/// notes whose title or description contains the trimmed query (case-insensitive),
/// then sort (the sort's default direction, reversed when [reversed] is set). Pure so
/// search/filter/sort stay consistent and stay unit-testable.
fun applyQuery(
    notes: List<Note>,
    search: String,
    sort: SortOrder,
    filter: NoteFilter,
    reversed: Boolean = false,
): List<Note> {
    val filtered = when (filter) {
        NoteFilter.ALL -> notes
        NoteFilter.PINNED -> notes.filter { it.isPinned }
        NoteFilter.UNPINNED -> notes.filter { !it.isPinned }
    }
    val query = search.trim()
    val matched = if (query.isEmpty()) {
        filtered
    } else {
        filtered.filter { note ->
            note.title.contains(query, ignoreCase = true) ||
                note.description.contains(query, ignoreCase = true)
        }
    }
    val ordered = when (sort) {
        SortOrder.MODIFIED -> matched.sortedByDescending { it.updatedAt }
        SortOrder.CREATED -> matched.sortedByDescending { it.createdAt }
        SortOrder.TITLE -> matched.sortedBy { it.title.lowercase() }
    }
    return if (reversed) ordered.reversed() else ordered
}
