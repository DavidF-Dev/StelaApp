package dev.davidfdev.stela.ui.notelist

import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.settings.NoteFilter
import dev.davidfdev.stela.settings.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteQueryTest {

    private fun note(
        id: Long,
        title: String = "",
        description: String = "",
        isPinned: Boolean = false,
        isArchived: Boolean = false,
        createdAt: Long = 0,
        updatedAt: Long = 0,
    ) = Note(
        id = id,
        title = title,
        description = description,
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ids(notes: List<Note>) = notes.map { it.id }

    @Test
    fun filterAll_keepsEveryNote() {
        val notes = listOf(note(1, isPinned = true), note(2))
        assertEquals(setOf(1L, 2L), applyQuery(notes, "", SortOrder.MODIFIED, NoteFilter.ALL).map { it.id }.toSet())
    }

    @Test
    fun archivedNotes_areExcluded_fromEveryFilter() {
        val notes = listOf(note(1), note(2, isArchived = true), note(3, isPinned = true, isArchived = true))
        // Archived notes live in their own destination and never appear in the main list.
        assertEquals(listOf(1L), ids(applyQuery(notes, "", SortOrder.MODIFIED, NoteFilter.ALL)))
        assertEquals(emptyList<Long>(), ids(applyQuery(notes, "", SortOrder.MODIFIED, NoteFilter.PINNED)))
        assertEquals(listOf(1L), ids(applyQuery(notes, "", SortOrder.MODIFIED, NoteFilter.UNPINNED)))
    }

    @Test
    fun filterPinned_keepsOnlyPinned() {
        val notes = listOf(note(1, isPinned = true), note(2, isPinned = false))
        assertEquals(listOf(1L), ids(applyQuery(notes, "", SortOrder.MODIFIED, NoteFilter.PINNED)))
    }

    @Test
    fun filterUnpinned_keepsOnlyUnpinned() {
        val notes = listOf(note(1, isPinned = true), note(2, isPinned = false))
        assertEquals(listOf(2L), ids(applyQuery(notes, "", SortOrder.MODIFIED, NoteFilter.UNPINNED)))
    }

    @Test
    fun emptySearch_keepsAll() {
        val notes = listOf(note(1, title = "Apple"), note(2, title = "Banana"))
        assertEquals(2, applyQuery(notes, "", SortOrder.TITLE, NoteFilter.ALL).size)
    }

    @Test
    fun search_matchesTitle_caseInsensitive() {
        val notes = listOf(note(1, title = "Grocery list"), note(2, title = "Work"))
        assertEquals(listOf(1L), ids(applyQuery(notes, "grocery", SortOrder.MODIFIED, NoteFilter.ALL)))
    }

    @Test
    fun search_matchesDescription_caseInsensitive() {
        val notes = listOf(note(1, title = "A", description = "Buy MILK"), note(2, title = "B", description = "eggs"))
        assertEquals(listOf(1L), ids(applyQuery(notes, "milk", SortOrder.MODIFIED, NoteFilter.ALL)))
    }

    @Test
    fun search_isTrimmed() {
        val notes = listOf(note(1, title = "Hello"), note(2, title = "World"))
        assertEquals(listOf(1L), ids(applyQuery(notes, "  hello  ", SortOrder.MODIFIED, NoteFilter.ALL)))
    }

    @Test
    fun search_noMatch_returnsEmpty() {
        val notes = listOf(note(1, title = "Hello"))
        assertEquals(emptyList<Long>(), ids(applyQuery(notes, "zzz", SortOrder.MODIFIED, NoteFilter.ALL)))
    }

    @Test
    fun sortModified_newestFirst() {
        val notes = listOf(note(1, updatedAt = 100), note(2, updatedAt = 300), note(3, updatedAt = 200))
        assertEquals(listOf(2L, 3L, 1L), ids(applyQuery(notes, "", SortOrder.MODIFIED, NoteFilter.ALL)))
    }

    @Test
    fun sortCreated_newestFirst() {
        val notes = listOf(note(1, createdAt = 100), note(2, createdAt = 300), note(3, createdAt = 200))
        assertEquals(listOf(2L, 3L, 1L), ids(applyQuery(notes, "", SortOrder.CREATED, NoteFilter.ALL)))
    }

    @Test
    fun sortTitle_alphabetical_caseInsensitive() {
        val notes = listOf(note(1, title = "banana"), note(2, title = "Apple"), note(3, title = "cherry"))
        assertEquals(listOf(2L, 1L, 3L), ids(applyQuery(notes, "", SortOrder.TITLE, NoteFilter.ALL)))
    }

    @Test
    fun reversed_modified_flipsToOldestFirst() {
        val notes = listOf(note(1, updatedAt = 100), note(2, updatedAt = 300), note(3, updatedAt = 200))
        assertEquals(
            listOf(1L, 3L, 2L),
            ids(applyQuery(notes, "", SortOrder.MODIFIED, NoteFilter.ALL, reversed = true)),
        )
    }

    @Test
    fun reversed_title_flipsToZtoA() {
        val notes = listOf(note(1, title = "banana"), note(2, title = "Apple"), note(3, title = "cherry"))
        assertEquals(
            listOf(3L, 1L, 2L),
            ids(applyQuery(notes, "", SortOrder.TITLE, NoteFilter.ALL, reversed = true)),
        )
    }

    @Test
    fun reversedDefault_isNaturalOrder() {
        val notes = listOf(note(1, updatedAt = 100), note(2, updatedAt = 300))
        // The default (reversed = false) matches the unreversed call.
        assertEquals(
            ids(applyQuery(notes, "", SortOrder.MODIFIED, NoteFilter.ALL)),
            ids(applyQuery(notes, "", SortOrder.MODIFIED, NoteFilter.ALL, reversed = false)),
        )
    }

    @Test
    fun combined_filterThenSearchThenSort() {
        val notes = listOf(
            note(1, title = "Shopping", description = "milk", isPinned = true, updatedAt = 100),
            note(2, title = "Shop tools", description = "", isPinned = true, updatedAt = 300),
            note(3, title = "Shopping trip", description = "", isPinned = false, updatedAt = 200),
        )
        // Pinned only, title contains "shop", newest-modified first → 2 then 1 (3 is unpinned).
        assertEquals(listOf(2L, 1L), ids(applyQuery(notes, "shop", SortOrder.MODIFIED, NoteFilter.PINNED)))
    }
}
