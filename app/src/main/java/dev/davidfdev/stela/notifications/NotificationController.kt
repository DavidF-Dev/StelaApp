package dev.davidfdev.stela.notifications

import dev.davidfdev.stela.data.Note

/// The only abstraction the rest of the app uses to drive pinned-note
/// notifications. Its sole implementation is the single class permitted to touch
/// the platform NotificationManager.
interface NotificationController {
    fun pin(note: Note)
    fun unpin(noteId: Long)
    fun refresh(note: Note)
}
