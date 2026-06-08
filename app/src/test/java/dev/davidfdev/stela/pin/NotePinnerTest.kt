package dev.davidfdev.stela.pin

import dev.davidfdev.stela.data.FakeNoteDao
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.notifications.FakeNotificationController
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePinnerTest {

    @Test
    fun pin_persistsFlagThenPostsPinnedNote() = runTest {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val id = repository.create(title = "A", description = "")
        val controller = FakeNotificationController()
        val pinner = NotePinner(repository, controller)

        pinner.pin(repository.getById(id)!!)

        assertTrue(repository.getById(id)!!.isPinned)
        assertEquals(listOf(id), controller.pinned.map { it.id })
        assertTrue(controller.pinned.single().isPinned)
    }

    @Test
    fun unpin_persistsFlagThenCancels() = runTest {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val id = repository.create(title = "A", description = "")
        repository.setPinned(id, true)
        val controller = FakeNotificationController()
        val pinner = NotePinner(repository, controller)

        pinner.unpin(id)

        assertFalse(repository.getById(id)!!.isPinned)
        assertEquals(listOf(id), controller.unpinned)
    }

    @Test
    fun refresh_reposts_onlyWhenPinned() = runTest {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val controller = FakeNotificationController()
        val pinner = NotePinner(repository, controller)
        val id = repository.create(title = "A", description = "")
        val note = repository.getById(id)!!

        pinner.refresh(note.copy(isPinned = false))
        assertTrue(controller.refreshed.isEmpty())

        pinner.refresh(note.copy(isPinned = true))
        assertEquals(listOf(id), controller.refreshed.map { it.id })
    }
}
