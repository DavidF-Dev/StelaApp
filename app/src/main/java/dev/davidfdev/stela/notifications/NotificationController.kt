package dev.davidfdev.stela.notifications

import dev.davidfdev.stela.data.Note

/// The only abstraction the rest of the app uses to drive pinned-note
/// notifications. Its sole implementation is the single class permitted to touch
/// the platform NotificationManager.
interface NotificationController {
    /// When true, pinned-note notifications are hidden on a secure lock screen.
    /// Set from the user's preference; applied to notifications built afterward.
    var hideOnLockScreen: Boolean

    /// When true, swiping a pinned notification unpins it instead of self-healing.
    /// Set from the user's preference; applied to notifications built afterward.
    var swipeToUnpin: Boolean

    fun pin(note: Note)
    fun unpin(noteId: Long)
    fun refresh(note: Note)

    /// Builds the quick-add notification that the foreground service shows as its
    /// mandatory ongoing notification.
    fun buildQuickAddNotification(): android.app.Notification

    /// Builds the minimal "running" notification the service shows when quick-add is
    /// disabled but it must stay alive to keep pinned notes posted.
    fun buildServiceRunningNotification(): android.app.Notification
}
