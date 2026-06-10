package dev.davidfdev.stela.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.davidfdev.stela.StelaApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReassertOnClearTest {

    private lateinit var context: Context
    private lateinit var app: StelaApp
    private lateinit var manager: NotificationManager
    private val createdIds = mutableListOf<Long>()

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        app = context.applicationContext as StelaApp
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
    }

    @After
    fun tearDown() {
        manager.cancelAll()
        runBlocking {
            createdIds.forEach { id ->
                app.container.noteRepository.getById(id)?.let { app.container.noteRepository.delete(it) }
            }
        }
    }

    @Test
    fun reassert_repostsNotification_whenStillPinned() = runBlocking {
        val repository = app.container.noteRepository
        val id = repository.create(title = "Pinned", description = "x").also { createdIds += it }
        repository.setPinned(id, true)
        manager.cancelAll()

        context.sendBroadcast(NotificationActionReceiver.reassertIntent(context, id))

        waitUntil { manager.activeNotifications.any { it.id == notificationId(id) } }
        assertTrue(manager.activeNotifications.any { it.id == notificationId(id) })
    }

    @Test
    fun reassertService_repostsServiceNotification_whenServiceShouldRun() = runBlocking {
        val repository = app.container.noteRepository
        // A pinned note forces the service-should-run rule true regardless of quick-add.
        val id = repository.create(title = "Pinned", description = "x").also { createdIds += it }
        repository.setPinned(id, true)
        manager.cancelAll()

        context.sendBroadcast(NotificationActionReceiver.reassertServiceIntent(context))

        waitUntil {
            manager.activeNotifications.any { it.id == AndroidNotificationController.QUICK_ADD_NOTIFICATION_ID }
        }
        assertTrue(
            manager.activeNotifications.any { it.id == AndroidNotificationController.QUICK_ADD_NOTIFICATION_ID },
        )
    }

    @Test
    fun reassert_doesNothing_whenNotPinned() = runBlocking {
        val repository = app.container.noteRepository
        val id = repository.create(title = "Unpinned", description = "x").also { createdIds += it }
        manager.cancelAll()

        context.sendBroadcast(NotificationActionReceiver.reassertIntent(context, id))
        Thread.sleep(800)

        assertFalse(manager.activeNotifications.any { it.id == notificationId(id) })
    }

    private fun waitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(50)
        }
    }
}
