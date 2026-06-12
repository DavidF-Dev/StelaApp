package dev.davidfdev.stela.notifications

import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.settings.RemovalPreference

/// The only abstraction the rest of the app uses to drive pinned-note
/// notifications. Its sole implementation is the single class permitted to touch
/// the platform NotificationManager.
interface NotificationController {
    /// When true, pinned-note notifications are hidden on a secure lock screen.
    /// Set from the user's preference; applied to notifications built afterward.
    var hideOnLockScreen: Boolean

    /// When true, swiping a pinned notification removes it (per [removalPreference]) instead of
    /// self-healing. Set from the user's preference; applied to notifications built afterward.
    var swipeToRemove: Boolean

    /// What the notification's remove action — and a swipe when [swipeToRemove] is on — does to the
    /// note. Set from the user's preference; applied to notifications built afterward.
    var removalPreference: RemovalPreference

    fun pin(note: Note)
    fun unpin(noteId: Long)
    fun refresh(note: Note)

    /// Builds the quick-add notification that the foreground service shows as its
    /// mandatory ongoing notification.
    fun buildQuickAddNotification(): android.app.Notification

    /// Builds the minimal "running" notification the service shows when quick-add is
    /// disabled but it must stay alive to keep pinned notes posted.
    fun buildServiceRunningNotification(): android.app.Notification

    /// Re-posts the foreground-service notification (quick-add or "running") after the
    /// user swipes it away — the self-heal triggered by its deleteIntent.
    fun reassertServiceNotification(quickAddEnabled: Boolean)
}
