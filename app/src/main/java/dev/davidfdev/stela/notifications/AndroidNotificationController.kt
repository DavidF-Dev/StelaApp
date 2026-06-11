package dev.davidfdev.stela.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import dev.davidfdev.stela.MainActivity
import dev.davidfdev.stela.R
import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.data.displayTitle
import dev.davidfdev.stela.ui.quicknote.QuickNoteActivity

/// The sole class that touches the platform notification system. Builds an ongoing
/// notification per pinned note with Edit and Remove actions, and creates the
/// pinned-notes channel on construction.
class AndroidNotificationController(private val context: Context) : NotificationController {

    private val manager = NotificationManagerCompat.from(context)

    @Volatile
    override var hideOnLockScreen: Boolean = false

    @Volatile
    override var swipeToUnpin: Boolean = false

    init {
        val pinned = NotificationChannelCompat.Builder(CHANNEL_PINNED, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.channel_pinned_name))
            .setShowBadge(false)
            .setVibrationEnabled(false)
            .setSound(null, null)
            .build()
        val quickAdd = NotificationChannelCompat.Builder(CHANNEL_QUICK_ADD, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.channel_quick_add_name))
            .setShowBadge(false)
            .setVibrationEnabled(false)
            .setSound(null, null)
            .build()
        val serviceStatus = NotificationChannelCompat.Builder(CHANNEL_SERVICE_STATUS, NotificationManagerCompat.IMPORTANCE_MIN)
            .setName(context.getString(R.string.channel_service_status_name))
            .setShowBadge(false)
            .setVibrationEnabled(false)
            .setSound(null, null)
            .build()
        manager.createNotificationChannel(pinned)
        manager.createNotificationChannel(quickAdd)
        manager.createNotificationChannel(serviceStatus)
    }

    override fun pin(note: Note) = post(note)

    override fun refresh(note: Note) = post(note)

    override fun unpin(noteId: Long) = manager.cancel(notificationId(noteId))

    // The caller gates posting behind POST_NOTIFICATIONS; lint cannot see that.
    @SuppressLint("MissingPermission")
    override fun reassertServiceNotification(quickAddEnabled: Boolean) {
        val notification = if (quickAddEnabled) buildQuickAddNotification() else buildServiceRunningNotification()
        manager.notify(QUICK_ADD_NOTIFICATION_ID, notification)
    }

    // The caller gates posting behind POST_NOTIFICATIONS; lint cannot see that.
    @SuppressLint("MissingPermission")
    private fun post(note: Note) {
        val visibility =
            if (hideOnLockScreen) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PUBLIC
        val builder = NotificationCompat.Builder(context, CHANNEL_PINNED)
            .setSmallIcon(R.drawable.ic_stela_pin)
            .setColor(context.getColor(R.color.brand_indigo))
            .setContentTitle(note.displayTitle)
            .setContentIntent(editIntent(note.id))
            // Non-ongoing (swipeable) when the user opted into swipe-to-unpin.
            .setOngoing(!swipeToUnpin)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(visibility)
            // On swipe: unpin if the user opted in, else self-heal by re-posting.
            .setDeleteIntent(if (swipeToUnpin) unpinIntent(note.id) else reassertIntent(note.id))
            .addAction(0, context.getString(R.string.notification_action_edit), editIntent(note.id))
            .addAction(0, context.getString(R.string.notification_action_unpin), unpinIntent(note.id))
            .addAction(0, context.getString(R.string.notification_action_archive), archiveIntent(note.id))
        if (note.description.isNotBlank()) {
            builder.setContentText(note.description)
                .setStyle(NotificationCompat.BigTextStyle().bigText(note.description))
        } else {
            // Title-only note: show an action hint instead of a blank content line.
            builder.setContentText(context.getString(R.string.notification_pinned_hint))
        }
        manager.notify(notificationId(note.id), builder.build())
    }

    // Body tap and the Edit action open the quick-note popup over the screen for this note.
    private fun editIntent(noteId: Long): PendingIntent {
        val intent = QuickNoteActivity.editNoteIntent(context, noteId)
        return PendingIntent.getActivity(context, notificationId(noteId), intent, PENDING_FLAGS)
    }

    private fun unpinIntent(noteId: Long): PendingIntent {
        val intent = NotificationActionReceiver.unpinIntent(context, noteId)
        return PendingIntent.getBroadcast(context, notificationId(noteId), intent, PENDING_FLAGS)
    }

    private fun archiveIntent(noteId: Long): PendingIntent {
        val intent = NotificationActionReceiver.archiveIntent(context, noteId)
        return PendingIntent.getBroadcast(context, notificationId(noteId), intent, PENDING_FLAGS)
    }

    private fun reassertIntent(noteId: Long): PendingIntent {
        val intent = NotificationActionReceiver.reassertIntent(context, noteId)
        return PendingIntent.getBroadcast(context, notificationId(noteId), intent, PENDING_FLAGS)
    }

    private fun reassertServiceIntent(): PendingIntent {
        val intent = NotificationActionReceiver.reassertServiceIntent(context)
        return PendingIntent.getBroadcast(context, REASSERT_SERVICE_REQUEST, intent, PENDING_FLAGS)
    }

    // Body tap and the New note action open the quick-note popup on a fresh note that pins on save;
    // View notes opens the list.
    override fun buildQuickAddNotification(): android.app.Notification {
        val newNote = popupNewNoteIntent()
        return NotificationCompat.Builder(context, CHANNEL_QUICK_ADD)
            .setSmallIcon(R.drawable.ic_stela_pin)
            .setColor(context.getColor(R.color.brand_indigo))
            // A service-only notification, not note content — never show it on the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentTitle(context.getString(R.string.quick_add_title))
            .setContentText(context.getString(R.string.quick_add_text))
            .setContentIntent(newNote)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            // Android 14+ lets users swipe ongoing notifications away; re-post on swipe.
            .setDeleteIntent(reassertServiceIntent())
            .addAction(0, context.getString(R.string.notification_action_new_note), newNote)
            .addAction(0, context.getString(R.string.notification_action_view_notes), deepLinkActivityIntent("$DEEP_LINK_BASE/list", QUICK_ADD_VIEW_REQUEST))
            .build()
    }

    override fun buildServiceRunningNotification(): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE_STATUS)
            .setSmallIcon(R.drawable.ic_stela_pin)
            .setColor(context.getColor(R.color.brand_indigo))
            // A service-only notification, not note content — never show it on the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentTitle(context.getString(R.string.service_running_title))
            .setContentText(context.getString(R.string.service_running_text))
            .setContentIntent(deepLinkActivityIntent("$DEEP_LINK_BASE/list", SERVICE_RUNNING_REQUEST))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            // Android 14+ lets users swipe ongoing notifications away; re-post on swipe.
            .setDeleteIntent(reassertServiceIntent())
            .build()

    private fun popupNewNoteIntent(): PendingIntent {
        val intent = QuickNoteActivity.newNoteIntent(context)
        return PendingIntent.getActivity(context, QUICK_ADD_NEW_REQUEST, intent, PENDING_FLAGS)
    }

    private fun deepLinkActivityIntent(uri: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri(), context, MainActivity::class.java)
        return PendingIntent.getActivity(context, requestCode, intent, PENDING_FLAGS)
    }

    companion object {
        const val CHANNEL_PINNED = "pinned_notes"
        const val CHANNEL_QUICK_ADD = "quick_add"
        const val CHANNEL_SERVICE_STATUS = "service_status"
        // Deep-link base is scheme://host; the scheme alone identifies our intents.
        const val DEEP_LINK_SCHEME = "stela"
        const val DEEP_LINK_BASE = "$DEEP_LINK_SCHEME://stela"

        // Reserved id outside the note-id space (note ids are positive, from 1).
        const val QUICK_ADD_NOTIFICATION_ID = -1

        private const val QUICK_ADD_NEW_REQUEST = -2
        private const val QUICK_ADD_VIEW_REQUEST = -3
        private const val SERVICE_RUNNING_REQUEST = -4
        private const val REASSERT_SERVICE_REQUEST = -5
        private const val PENDING_FLAGS =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
