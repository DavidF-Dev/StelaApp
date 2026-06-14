package dev.davidfdev.stela.ui.notelist

import dev.davidfdev.stela.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduledEventTest {

    private fun note(
        isPinned: Boolean = false,
        pinAt: Long? = null,
        unpinAt: Long? = null,
    ) = Note(
        title = "t",
        description = "",
        createdAt = 0L,
        updatedAt = 0L,
        isPinned = isPinned,
        pinAt = pinAt,
        unpinAt = unpinAt,
    )

    @Test
    fun unpinnedWithPinAt_isAPinEvent() {
        val event = note(isPinned = false, pinAt = 5_000L).scheduledEvent()
        assertEquals(ScheduledEvent(5_000L, isUnpin = false), event)
    }

    @Test
    fun pinnedWithUnpinAt_isAnUnpinEvent() {
        val event = note(isPinned = true, unpinAt = 9_000L).scheduledEvent()
        assertEquals(ScheduledEvent(9_000L, isUnpin = true), event)
    }

    @Test
    fun unpinnedWithBoth_showsThePinEventNext() {
        val event = note(isPinned = false, pinAt = 5_000L, unpinAt = 9_000L).scheduledEvent()
        assertEquals(ScheduledEvent(5_000L, isUnpin = false), event)
    }

    @Test
    fun unscheduled_hasNoEvent() {
        assertNull(note().scheduledEvent())
    }

    @Test
    fun pinnedWithoutUnpinAt_hasNoEvent() {
        assertNull(note(isPinned = true).scheduledEvent())
    }

    @Test
    fun unpinnedWithOnlyUnpinAt_hasNoEvent() {
        // An anomalous combination the editor prevents; defensively shows nothing.
        assertNull(note(isPinned = false, unpinAt = 9_000L).scheduledEvent())
    }
}
