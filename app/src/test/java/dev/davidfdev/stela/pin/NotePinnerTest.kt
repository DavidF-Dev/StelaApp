package dev.davidfdev.stela.pin

import dev.davidfdev.stela.data.FakeNoteDao
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.notifications.FakeNotificationController
import dev.davidfdev.stela.settings.FakeSettingsRepository
import dev.davidfdev.stela.settings.Settings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePinnerTest {

    private class Fixture(quickAddEnabled: Boolean = true) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val controller = FakeNotificationController()
        val service = FakeServiceController()
        val settings = FakeSettingsRepository(Settings(quickAddEnabled = quickAddEnabled))
        val pinner = NotePinner(repository, controller, service, settings)
    }

    @Test
    fun pin_persistsFlag_postsNotification_andStartsService() = runTest {
        val f = Fixture()
        val id = f.repository.create(title = "A", description = "")

        f.pinner.pin(f.repository.getById(id)!!)

        assertTrue(f.repository.getById(id)!!.isPinned)
        assertEquals(listOf(id), f.controller.pinned.map { it.id })
        assertEquals(1, f.service.startCount)
    }

    @Test
    fun unpinLast_withQuickAddDisabled_stopsService() = runTest {
        val f = Fixture(quickAddEnabled = false)
        val id = f.repository.create(title = "A", description = "")
        f.pinner.pin(f.repository.getById(id)!!)

        f.pinner.unpin(id)

        assertFalse(f.repository.getById(id)!!.isPinned)
        assertEquals(1, f.service.stopCount)
    }

    @Test
    fun unpinLast_withQuickAddEnabled_keepsServiceRunning() = runTest {
        val f = Fixture(quickAddEnabled = true)
        val id = f.repository.create(title = "A", description = "")
        f.pinner.pin(f.repository.getById(id)!!)

        f.pinner.unpin(id)

        // Quick-add keeps the service alive even with nothing pinned.
        assertEquals(0, f.service.stopCount)
    }

    @Test
    fun unpinOneOfTwo_keepsServiceRunning() = runTest {
        val f = Fixture(quickAddEnabled = false)
        val a = f.repository.create(title = "A", description = "")
        val b = f.repository.create(title = "B", description = "")
        f.pinner.pin(f.repository.getById(a)!!)
        f.pinner.pin(f.repository.getById(b)!!)
        val stopsBefore = f.service.stopCount

        f.pinner.unpin(a)

        assertEquals(stopsBefore, f.service.stopCount)
    }

    @Test
    fun pinAll_pinsEvery_postsEach_andReconcilesOnce() = runTest {
        val f = Fixture()
        val a = f.repository.create(title = "A", description = "")
        val b = f.repository.create(title = "B", description = "")

        f.pinner.pinAll(listOf(f.repository.getById(a)!!, f.repository.getById(b)!!))

        assertTrue(f.repository.getById(a)!!.isPinned)
        assertTrue(f.repository.getById(b)!!.isPinned)
        assertEquals(listOf(a, b), f.controller.pinned.map { it.id })
        // One reconcile for the whole batch, not one per note.
        assertEquals(1, f.service.startCount)
    }

    @Test
    fun unpinAll_unpinsEvery_andReconcilesOnce() = runTest {
        val f = Fixture(quickAddEnabled = false)
        val a = f.repository.create(title = "A", description = "")
        val b = f.repository.create(title = "B", description = "")
        f.pinner.pinAll(listOf(f.repository.getById(a)!!, f.repository.getById(b)!!))
        val stopsBefore = f.service.stopCount

        f.pinner.unpinAll(listOf(a, b))

        assertFalse(f.repository.getById(a)!!.isPinned)
        assertFalse(f.repository.getById(b)!!.isPinned)
        assertEquals(stopsBefore + 1, f.service.stopCount)
    }

    @Test
    fun delete_pinnedNote_cancelsNotification_andStopsService() = runTest {
        val f = Fixture(quickAddEnabled = false)
        val id = f.repository.create(title = "A", description = "")
        f.pinner.pin(f.repository.getById(id)!!)

        f.pinner.delete(f.repository.getById(id)!!)

        assertEquals(null, f.repository.getById(id))
        assertEquals(listOf(id), f.controller.unpinned)
        assertEquals(1, f.service.stopCount)
    }

    @Test
    fun delete_unpinnedNote_leavesNotificationsUntouched() = runTest {
        val f = Fixture(quickAddEnabled = false)
        val id = f.repository.create(title = "A", description = "")

        f.pinner.delete(f.repository.getById(id)!!)

        assertEquals(null, f.repository.getById(id))
        assertTrue(f.controller.unpinned.isEmpty())
    }

    @Test
    fun deleteAll_cancelsPinnedOnly_andReconcilesOnce() = runTest {
        val f = Fixture(quickAddEnabled = false)
        val a = f.repository.create(title = "A", description = "")
        val b = f.repository.create(title = "B", description = "")
        val c = f.repository.create(title = "C", description = "")
        f.pinner.pinAll(listOf(f.repository.getById(a)!!, f.repository.getById(b)!!))
        val stopsBefore = f.service.stopCount

        f.pinner.deleteAll(
            listOf(
                f.repository.getById(a)!!,
                f.repository.getById(b)!!,
                f.repository.getById(c)!!,
            ),
        )

        assertEquals(null, f.repository.getById(a))
        assertEquals(null, f.repository.getById(b))
        assertEquals(null, f.repository.getById(c))
        assertEquals(listOf(a, b), f.controller.unpinned)
        assertEquals(stopsBefore + 1, f.service.stopCount)
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
