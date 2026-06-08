package dev.davidfdev.stela.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationIdsTest {

    @Test
    fun sameNoteId_alwaysMapsToSameNotificationId() {
        assertEquals(notificationId(42L), notificationId(42L))
    }

    @Test
    fun distinctNoteIds_mapToDistinctNotificationIds() {
        assertNotEquals(notificationId(1L), notificationId(2L))
    }
}
