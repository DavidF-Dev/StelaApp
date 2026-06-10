package dev.davidfdev.stela.notifications

import dev.davidfdev.stela.data.Note

/// Records calls so JVM tests can assert what the pin flow posted, without the
/// platform NotificationManager.
class FakeNotificationController : NotificationController {
    val pinned = mutableListOf<Note>()
    val unpinned = mutableListOf<Long>()
    val refreshed = mutableListOf<Note>()
    val serviceReasserts = mutableListOf<Boolean>()

    override var hideOnLockScreen: Boolean = false
    override var swipeToUnpin: Boolean = false

    override fun pin(note: Note) { pinned += note }
    override fun unpin(noteId: Long) { unpinned += noteId }
    override fun refresh(note: Note) { refreshed += note }

    // The service notifications are built only inside the running service; JVM
    // tests never call these.
    override fun buildQuickAddNotification(): android.app.Notification =
        throw UnsupportedOperationException("not used in JVM tests")

    override fun buildServiceRunningNotification(): android.app.Notification =
        throw UnsupportedOperationException("not used in JVM tests")

    override fun reassertServiceNotification(quickAddEnabled: Boolean) { serviceReasserts += quickAddEnabled }
}
