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
    fun updateContent_bumpsUpdatedAt_butLeavesPinAndSchedule() = runTest {
        now = 1_000L
        val id = repository.create(title = "Old", description = "old body")
        repository.setPinned(id, true)
        repository.setSchedule(id, pinAt = 5_000L, unpinAt = 9_000L)

        now = 7_000L
        repository.updateContent(id, title = "New", description = "new body", emoji = "📝")

        val updated = dao.getById(id)!!
        assertEquals("New", updated.title)
        assertEquals("new body", updated.description)
        assertEquals("📝", updated.emoji)
        assertEquals(7_000L, updated.updatedAt)
        // A content write must not touch pin/schedule — the guarantee that makes a stale-editor save safe.
        assertTrue(updated.isPinned)
        assertEquals(5_000L, updated.pinAt)
        assertEquals(9_000L, updated.unpinAt)
    }

    @Test
    fun observeById_reflectsMutations_andNullsAfterDelete() = runTest {
        val id = repository.create(title = "Watched", description = "")

        assertEquals("Watched", repository.observeById(id).first()!!.title)

        repository.setPinned(id, true)
        assertTrue(repository.observeById(id).first()!!.isPinned)

        repository.delete(dao.getById(id)!!)
        assertNull(repository.observeById(id).first())
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

    @Test
    fun setPinned_flipsFlag_withoutBumpingUpdatedAt() = runTest {
        now = 1_000L
        val id = repository.create(title = "Pin me", description = "")
        val before = dao.getById(id)!!

        now = 9_000L
        repository.setPinned(id, true)

        val after = dao.getById(id)!!
        assertTrue(after.isPinned)
        assertEquals(before.updatedAt, after.updatedAt)
    }

    @Test
    fun countPinned_countsOnlyPinnedNotes() = runTest {
        val a = repository.create(title = "A", description = "")
        repository.create(title = "B", description = "")
        assertEquals(0, repository.countPinned())

        repository.setPinned(a, true)
        assertEquals(1, repository.countPinned())
    }

    @Test
    fun importNotes_insertsWithFreshIds_preservingFields_andUnpinned() = runTest {
        // An imported note (id 0 from decode), carrying its original timestamps and emoji.
        val imported = Note(
            id = 0,
            title = "Imported",
            description = "body",
            emoji = "📄",
            isPinned = false,
            createdAt = 111,
            updatedAt = 222,
        )

        val count = repository.importNotes(listOf(imported))

        assertEquals(1, count)
        val stored = repository.notes.first().single()
        assertTrue(stored.id > 0)
        assertEquals("Imported", stored.title)
        assertEquals("📄", stored.emoji)
        assertEquals(111L, stored.createdAt)
        assertEquals(222L, stored.updatedAt)
        assertFalse(stored.isPinned)
    }

    @Test
    fun importNotes_addsToExisting_withoutOverwriting() = runTest {
        repository.create(title = "Existing", description = "")

        repository.importNotes(
            listOf(Note(title = "New", description = "", createdAt = 1, updatedAt = 1)),
        )

        assertEquals(setOf("Existing", "New"), repository.notes.first().map { it.title }.toSet())
    }

    @Test
    fun setPinned_doesNotReorderList() = runTest {
        now = 1_000L
        val olderId = repository.create(title = "Older", description = "")
        now = 2_000L
        repository.create(title = "Newer", description = "")

        repository.setPinned(olderId, true)

        val titles = repository.notes.first().map { it.title }
        assertEquals(listOf("Newer", "Older"), titles)
    }
}
