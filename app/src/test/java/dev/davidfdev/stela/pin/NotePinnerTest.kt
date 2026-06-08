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

    private class Fixture {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val controller = FakeNotificationController()
        val service = FakeServiceController()
        val pinner = NotePinner(repository, controller, service)
    }

    @Test
    fun pin_persistsFlag_postsNotification_andStartsService() = runTest {
        val f = Fixture()
        val id = f.repository.create(title = "A", description = "")

        f.pinner.pin(f.repository.getById(id)!!)

        assertTrue(f.repository.getById(id)!!.isPinned)
        assertEquals(listOf(id), f.controller.pinned.map { it.id })
        assertTrue(f.controller.pinned.single().isPinned)
        assertEquals(1, f.service.startCount)
        assertEquals(0, f.service.stopCount)
    }

    @Test
    fun unpin_lastPinned_cancelsNotification_andStopsService() = runTest {
        val f = Fixture()
        val id = f.repository.create(title = "A", description = "")
        f.pinner.pin(f.repository.getById(id)!!)

        f.pinner.unpin(id)

        assertFalse(f.repository.getById(id)!!.isPinned)
        assertEquals(listOf(id), f.controller.unpinned)
        assertEquals(1, f.service.stopCount)
    }

    @Test
    fun unpin_oneOfTwo_keepsServiceRunning() = runTest {
        val f = Fixture()
        val a = f.repository.create(title = "A", description = "")
        val b = f.repository.create(title = "B", description = "")
        f.pinner.pin(f.repository.getById(a)!!)
        f.pinner.pin(f.repository.getById(b)!!)
        val stopsBefore = f.service.stopCount

        f.pinner.unpin(a)

        // A note is still pinned, so the service must not be stopped.
        assertEquals(stopsBefore, f.service.stopCount)
    }

    @Test
    fun refresh_reposts_onlyWhenPinned() = runTest {
        val f = Fixture()
        val id = f.repository.create(title = "A", description = "")
        val note = f.repository.getById(id)!!

        f.pinner.refresh(note.copy(isPinned = false))
        assertTrue(f.controller.refreshed.isEmpty())

        f.pinner.refresh(note.copy(isPinned = true))
        assertEquals(listOf(id), f.controller.refreshed.map { it.id })
    }
}
