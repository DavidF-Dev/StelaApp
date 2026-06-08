package dev.davidfdev.stela.notifications

import dev.davidfdev.stela.data.Note

/// Records calls so JVM tests can assert what the pin flow posted, without the
/// platform NotificationManager.
class FakeNotificationController : NotificationController {
    val pinned = mutableListOf<Note>()
    val unpinned = mutableListOf<Long>()
    val refreshed = mutableListOf<Note>()

    override fun pin(note: Note) { pinned += note }
    override fun unpin(noteId: Long) { unpinned += noteId }
    override fun refresh(note: Note) { refreshed += note }
}
