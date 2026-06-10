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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationActionReceiverTest {

    private lateinit var context: Context
    private lateinit var app: StelaApp
    private lateinit var manager: NotificationManager
    private var createdNoteId: Long = -1L

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
            app.container.noteRepository.getById(createdNoteId)?.let {
                app.container.noteRepository.delete(it)
            }
        }
    }

    @Test
    fun unpinAction_unpinsNoteAndCancelsNotification() = runBlocking {
        val repository = app.container.noteRepository
        createdNoteId = repository.create(title = "Pinned", description = "x")
        // Post the pinned notification directly (not via NotePinner) so this test
        // does not start the foreground service from a background context.
        repository.setPinned(createdNoteId, true)
        app.container.notificationController.pin(repository.getById(createdNoteId)!!)
        val id = notificationId(createdNoteId)
        waitUntil { manager.activeNotifications.any { it.id == id } }

        context.sendBroadcast(NotificationActionReceiver.unpinIntent(context, createdNoteId))

        waitUntil(5_000) { manager.activeNotifications.none { it.id == id } }
        assertFalse(manager.activeNotifications.any { it.id == id })
        assertFalse(repository.getById(createdNoteId)!!.isPinned)
    }

    private fun waitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(50)
        }
    }
}
