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

    private class Fixture(quickAddEnabled: Boolean = true, now: Long = 1_000L) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val controller = FakeNotificationController()
        val service = FakeServiceController()
        val settings = FakeSettingsRepository(Settings(quickAddEnabled = quickAddEnabled))
        val scheduler = FakePinScheduler()
        val pinner = NotePinner(repository, controller, service, settings, scheduler) { now }
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
    fun restore_reinsertsNotes_repinsPinned_andReconcilesOnce() = runTest {
        val f = Fixture(quickAddEnabled = false)
        val a = f.repository.create(title = "A", description = "")
        val b = f.repository.create(title = "B", description = "")
        f.pinner.pin(f.repository.getById(a)!!)
        val deleted = listOf(f.repository.getById(a)!!, f.repository.getById(b)!!)
        f.pinner.deleteAll(deleted)
        val startsBefore = f.service.startCount
        f.controller.pinned.clear()

        f.pinner.restore(deleted)

        // Both notes are back, with their original ids and pin state preserved.
        assertEquals("A", f.repository.getById(a)!!.title)
        assertTrue(f.repository.getById(a)!!.isPinned)
        assertFalse(f.repository.getById(b)!!.isPinned)
        // Only the pinned one re-posts its notification; one reconcile for the batch.
        assertEquals(listOf(a), f.controller.pinned.map { it.id })
        assertEquals(startsBefore + 1, f.service.startCount)
    }

    @Test
    fun archive_pinnedNote_unpins_cancelsNotification_andSetsArchived() = runTest {
        val f = Fixture(quickAddEnabled = false)
        val id = f.repository.create(title = "A", description = "")
        f.pinner.pin(f.repository.getById(id)!!)

        f.pinner.archive(f.repository.getById(id)!!)

        val note = f.repository.getById(id)!!
        assertTrue(note.isArchived)
        assertFalse(note.isPinned)
        assertEquals(listOf(id), f.controller.unpinned)
        // Last pinned note archived with quick-add off: the service stops.
        assertEquals(1, f.service.stopCount)
    }

    @Test
    fun archive_unpinnedNote_leavesNotificationsUntouched() = runTest {
        val f = Fixture()
        val id = f.repository.create(title = "A", description = "")

        f.pinner.archive(f.repository.getById(id)!!)

        assertTrue(f.repository.getById(id)!!.isArchived)
        assertTrue(f.controller.unpinned.isEmpty())
    }

    @Test
    fun archiveAll_cancelsPinnedOnly_andReconcilesOnce() = runTest {
        val f = Fixture(quickAddEnabled = false)
        val a = f.repository.create(title = "A", description = "")
        val b = f.repository.create(title = "B", description = "")
        f.pinner.pin(f.repository.getById(a)!!)
        val stopsBefore = f.service.stopCount

        f.pinner.archiveAll(listOf(f.repository.getById(a)!!, f.repository.getById(b)!!))

        assertTrue(f.repository.getById(a)!!.isArchived)
        assertTrue(f.repository.getById(b)!!.isArchived)
        assertEquals(listOf(a), f.controller.unpinned)
        assertEquals(stopsBefore + 1, f.service.stopCount)
    }

    @Test
    fun unarchive_clearsFlag_withoutRepostingOrServiceChange() = runTest {
        val f = Fixture(quickAddEnabled = false)
        val id = f.repository.create(title = "A", description = "")
        f.pinner.archive(f.repository.getById(id)!!)
        val startsBefore = f.service.startCount

        f.pinner.unarchive(f.repository.getById(id)!!)

        assertFalse(f.repository.getById(id)!!.isArchived)
        assertTrue(f.controller.pinned.isEmpty())
        assertEquals(startsBefore, f.service.startCount)
    }

    @Test
    fun pin_archivedNote_unarchivesAndPins() = runTest {
        val f = Fixture()
        val id = f.repository.create(title = "A", description = "")
        f.pinner.archive(f.repository.getById(id)!!)

        f.pinner.pin(f.repository.getById(id)!!)

        val note = f.repository.getById(id)!!
        assertTrue(note.isPinned)
        assertFalse(note.isArchived)
        assertEquals(listOf(id), f.controller.pinned.map { it.id })
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

    @Test
    fun applySchedule_futurePinAt_armsAlarmWithoutPinningNow() = runTest {
        val f = Fixture(now = 1_000L)
        val id = f.repository.create(title = "A", description = "")

        f.pinner.applySchedule(id, pinAt = 2_000L, unpinAt = null)

        assertFalse(f.repository.getById(id)!!.isPinned)
        assertEquals(2_000L, f.repository.getById(id)!!.pinAt)
        assertEquals(2_000L, f.scheduler.scheduledPins[id])
    }

    @Test
    fun applySchedule_pastPinAt_pinsNowAndClearsTheTime() = runTest {
        val f = Fixture(now = 1_000L)
        val id = f.repository.create(title = "A", description = "")

        f.pinner.applySchedule(id, pinAt = 500L, unpinAt = null)

        assertTrue(f.repository.getById(id)!!.isPinned)
        assertEquals(null, f.repository.getById(id)!!.pinAt)
        assertEquals(listOf(id), f.controller.pinned.map { it.id })
        assertFalse(f.scheduler.scheduledPins.containsKey(id))
    }

    @Test
    fun applySchedule_futureUnpinAt_armsUnpinAlarm() = runTest {
        val f = Fixture(now = 1_000L)
        val id = f.repository.create(title = "A", description = "")
        f.pinner.pin(f.repository.getById(id)!!)

        f.pinner.applySchedule(id, pinAt = null, unpinAt = 2_000L)

        assertTrue(f.repository.getById(id)!!.isPinned)
        assertEquals(2_000L, f.scheduler.scheduledUnpins[id])
    }

    @Test
    fun archive_keepsScheduleAndArmedAlarm() = runTest {
        val f = Fixture(now = 1_000L)
        val id = f.repository.create(title = "A", description = "")
        f.pinner.applySchedule(id, pinAt = 2_000L, unpinAt = null)

        f.pinner.archive(f.repository.getById(id)!!)

        val note = f.repository.getById(id)!!
        assertTrue(note.isArchived)
        // The schedule is kept (dormant), not dropped, and its alarm stays armed.
        assertEquals(2_000L, note.pinAt)
        assertEquals(2_000L, f.scheduler.scheduledPins[id])
        assertFalse(id in f.scheduler.cancelledPins)
    }

    @Test
    fun unarchive_reArmsKeptFuturePin_withoutPinningYet() = runTest {
        val f = Fixture(now = 1_000L)
        val id = f.repository.create(title = "A", description = "")
        f.pinner.applySchedule(id, pinAt = 2_000L, unpinAt = null)
        f.pinner.archive(f.repository.getById(id)!!)
        f.scheduler.scheduledPins.clear()

        f.pinner.unarchive(f.repository.getById(id)!!)

        val note = f.repository.getById(id)!!
        assertFalse(note.isArchived)
        assertFalse(note.isPinned)
        assertEquals(2_000L, note.pinAt)
        assertEquals(2_000L, f.scheduler.scheduledPins[id])
    }

    @Test
    fun unarchive_catchesUpPastDuePin() = runTest {
        val f = Fixture(now = 1_000L)
        val id = f.repository.create(title = "A", description = "")
        // A past-due pinAt that survived the archive (no alarm/reconcile cleared it).
        f.repository.setSchedule(id, pinAt = 500L, unpinAt = null)
        f.repository.setArchived(id, true)

        f.pinner.unarchive(f.repository.getById(id)!!)

        val note = f.repository.getById(id)!!
        assertFalse(note.isArchived)
        assertTrue(note.isPinned)
        assertEquals(null, note.pinAt)
        assertEquals(listOf(id), f.controller.pinned.map { it.id })
    }

    @Test
    fun unarchive_fullyElapsedWindow_returnsUnpinnedAndCleared() = runTest {
        val f = Fixture(now = 10_000L)
        val id = f.repository.create(title = "A", description = "")
        f.repository.setSchedule(id, pinAt = 2_000L, unpinAt = 5_000L)
        f.repository.setArchived(id, true)

        f.pinner.unarchive(f.repository.getById(id)!!)

        val note = f.repository.getById(id)!!
        assertFalse(note.isArchived)
        assertFalse(note.isPinned)
        assertEquals(null, note.pinAt)
        assertEquals(null, note.unpinAt)
    }

    @Test
    fun reconcileAll_firesPastDuePin() = runTest {
        val f = Fixture(now = 1_000L)
        val id = f.repository.create(title = "A", description = "")
        f.repository.setSchedule(id, pinAt = 500L, unpinAt = null)

        f.pinner.reconcileAll()

        assertTrue(f.repository.getById(id)!!.isPinned)
        assertEquals(null, f.repository.getById(id)!!.pinAt)
    }

    @Test
    fun pin_clearsPendingPinAt() = runTest {
        val f = Fixture(now = 1_000L)
        val id = f.repository.create(title = "A", description = "")
        f.repository.setSchedule(id, pinAt = 5_000L, unpinAt = 9_000L)

        f.pinner.pin(f.repository.getById(id)!!)

        val note = f.repository.getById(id)!!
        assertTrue(note.isPinned)
        assertEquals(null, note.pinAt)
        // Pinning leaves the auto-unpin window intact.
        assertEquals(9_000L, note.unpinAt)
        assertTrue(id in f.scheduler.cancelledPins)
    }

    @Test
    fun unpin_clearsPendingUnpinAt() = runTest {
        val f = Fixture(now = 1_000L)
        val id = f.repository.create(title = "A", description = "")
        f.pinner.pin(f.repository.getById(id)!!)
        f.repository.setSchedule(id, pinAt = null, unpinAt = 9_000L)

        f.pinner.unpin(id)

        val note = f.repository.getById(id)!!
        assertFalse(note.isPinned)
        assertEquals(null, note.unpinAt)
        assertTrue(id in f.scheduler.cancelledUnpins)
    }

    @Test
    fun snooze_hidesNow_setsPinAt_andKeepsUnpinAt() = runTest {
        val f = Fixture(now = 1_000L)
        val id = f.repository.create(title = "A", description = "")
        f.pinner.pin(f.repository.getById(id)!!)
        f.repository.setSchedule(id, pinAt = null, unpinAt = 9_000L)

        f.pinner.snooze(id, untilMillis = 5_000L)

        val note = f.repository.getById(id)!!
        assertFalse(note.isPinned)
        assertEquals(5_000L, note.pinAt)
        // Snooze, unlike a manual unpin, preserves the auto-unpin window.
        assertEquals(9_000L, note.unpinAt)
        assertEquals(5_000L, f.scheduler.scheduledPins[id])
    }
}
