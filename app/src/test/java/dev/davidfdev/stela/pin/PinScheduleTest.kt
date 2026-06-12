package dev.davidfdev.stela.pin

import org.junit.Assert.assertEquals
import org.junit.Test

class PinScheduleTest {

    private val now = 1_000L

    @Test
    fun futurePinAt_survivesAndLeavesStateUnchanged() {
        val r = PinSchedule.resolve(isPinned = false, isArchived = false, pinAt = 2_000L, unpinAt = null, now = now)
        assertEquals(PinSchedule.Resolution(targetPinned = false, pinAt = 2_000L, unpinAt = null), r)
    }

    @Test
    fun pastPinAt_pinsAndClears() {
        val r = PinSchedule.resolve(isPinned = false, isArchived = false, pinAt = 500L, unpinAt = null, now = now)
        assertEquals(PinSchedule.Resolution(targetPinned = true, pinAt = null, unpinAt = null), r)
    }

    @Test
    fun pastUnpinAt_unpinsAndClears() {
        val r = PinSchedule.resolve(isPinned = true, isArchived = false, pinAt = null, unpinAt = 500L, now = now)
        assertEquals(PinSchedule.Resolution(targetPinned = false, pinAt = null, unpinAt = null), r)
    }

    @Test
    fun insideWindow_pinsAndKeepsTheUnpinTime() {
        // pinAt past, unpinAt future: now is within the window, so the note should be pinned.
        val r = PinSchedule.resolve(isPinned = false, isArchived = false, pinAt = 500L, unpinAt = 2_000L, now = now)
        assertEquals(PinSchedule.Resolution(targetPinned = true, pinAt = null, unpinAt = 2_000L), r)
    }

    @Test
    fun wholeWindowPast_landsUnpinnedWithoutChurn() {
        // pinAt and unpinAt both past: the later (unpin) wins, so a long-missed window never pins.
        val r = PinSchedule.resolve(isPinned = false, isArchived = false, pinAt = 400L, unpinAt = 600L, now = now)
        assertEquals(PinSchedule.Resolution(targetPinned = false, pinAt = null, unpinAt = null), r)
    }

    @Test
    fun firedTransitionsApplyInTimeOrder_notFieldOrder() {
        // unpinAt before pinAt, both past: the later pinAt wins, so the note ends pinned.
        val r = PinSchedule.resolve(isPinned = false, isArchived = false, pinAt = 800L, unpinAt = 600L, now = now)
        assertEquals(PinSchedule.Resolution(targetPinned = true, pinAt = null, unpinAt = null), r)
    }

    @Test
    fun archived_dropsScheduleAndNeverPins() {
        val r = PinSchedule.resolve(isPinned = false, isArchived = true, pinAt = 500L, unpinAt = 2_000L, now = now)
        assertEquals(PinSchedule.Resolution(targetPinned = false, pinAt = null, unpinAt = null), r)
    }
}
