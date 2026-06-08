package dev.davidfdev.stela.pin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceLifecycleTest {

    @Test
    fun runs_whenAnyNotePinned() {
        assertTrue(ServiceLifecycle.shouldRun(pinnedCount = 1, quickAddEnabled = false))
    }

    @Test
    fun runs_whenQuickAddEnabled_evenWithNoPins() {
        assertTrue(ServiceLifecycle.shouldRun(pinnedCount = 0, quickAddEnabled = true))
    }

    @Test
    fun stops_whenNoPinsAndQuickAddDisabled() {
        assertFalse(ServiceLifecycle.shouldRun(pinnedCount = 0, quickAddEnabled = false))
    }
}
