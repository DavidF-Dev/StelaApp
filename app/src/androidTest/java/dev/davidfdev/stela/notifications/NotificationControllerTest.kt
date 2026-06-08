package dev.davidfdev.stela.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.davidfdev.stela.data.Note
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationControllerTest {

    private lateinit var context: Context
    private lateinit var controller: AndroidNotificationController
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        controller = AndroidNotificationController(context)
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
    }

    @After
    fun tearDown() {
        manager.cancelAll()
    }

    @Test
    fun pin_postsOngoingNotificationWithDeterministicId() {
        val id = notificationId(7L)

        controller.pin(pinnedNote(7L))

        waitUntil { manager.activeNotifications.any { it.id == id } }
        val posted = manager.activeNotifications.first { it.id == id }
        assertTrue(posted.isOngoing)
    }

    @Test
    fun pin_appliesSecretVisibility_whenHideOnLockScreenEnabled() {
        controller.hideOnLockScreen = true
        val id = notificationId(8L)

        controller.pin(pinnedNote(8L))

        waitUntil { manager.activeNotifications.any { it.id == id } }
        val posted = manager.activeNotifications.first { it.id == id }
        assertEquals(android.app.Notification.VISIBILITY_SECRET, posted.notification.visibility)
    }

    @Test
    fun unpin_cancelsNotification() {
        val id = notificationId(7L)
        controller.pin(pinnedNote(7L))
        waitUntil { manager.activeNotifications.any { it.id == id } }

        controller.unpin(7L)

        waitUntil { manager.activeNotifications.none { it.id == id } }
        assertFalse(manager.activeNotifications.any { it.id == id })
    }

    private fun pinnedNote(id: Long) =
        Note(id = id, title = "Title $id", description = "Body $id", isPinned = true, createdAt = 0, updatedAt = 0)

    private fun waitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(50)
        }
    }
}
