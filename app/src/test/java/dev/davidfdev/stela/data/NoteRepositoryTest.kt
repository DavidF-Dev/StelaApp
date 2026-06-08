package dev.davidfdev.stela.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteRepositoryTest {

    private val dao = FakeNoteDao()
    private var now = 1_000L
    private val repository = NoteRepository(dao) { now }

    @Test
    fun create_persistsNoteWithTimestampsAndDefaults() = runTest {
        now = 5_000L

        val id = repository.create(title = "Milk", description = "2L")

        val stored = dao.getById(id)!!
        assertEquals("Milk", stored.title)
        assertEquals("2L", stored.description)
        assertEquals(Note.DEFAULT_ICON_ID, stored.iconId)
        assertFalse(stored.isPinned)
        assertEquals(5_000L, stored.createdAt)
        assertEquals(5_000L, stored.updatedAt)
    }

    @Test
    fun update_bumpsUpdatedAt_butPreservesCreatedAt() = runTest {
        now = 1_000L
        val id = repository.create(title = "A", description = "")
        val original = dao.getById(id)!!

        now = 9_000L
        repository.update(original.copy(title = "A2"))

        val updated = dao.getById(id)!!
        assertEquals("A2", updated.title)
        assertEquals(1_000L, updated.createdAt)
        assertEquals(9_000L, updated.updatedAt)
    }

    @Test
    fun notes_emitsMostRecentlyUpdatedFirst() = runTest {
        now = 1_000L
        repository.create(title = "Older", description = "")
        now = 2_000L
        repository.create(title = "Newer", description = "")

        val titles = repository.notes.first().map { it.title }

        assertEquals(listOf("Newer", "Older"), titles)
    }

    @Test
    fun delete_removesNote() = runTest {
        now = 1_000L
        val id = repository.create(title = "Temp", description = "")

        repository.delete(dao.getById(id)!!)

        assertNull(dao.getById(id))
        assertTrue(repository.notes.first().isEmpty())
    }

    @Test
    fun getById_returnsNull_whenAbsent() = runTest {
        assertNull(repository.getById(42L))
    }
}
