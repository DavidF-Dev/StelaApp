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

/// The sole class that touches the platform notification system. Builds an ongoing
/// notification per pinned note with Edit and Remove actions, and creates the
/// pinned-notes channel on construction.
class AndroidNotificationController(private val context: Context) : NotificationController {

    private val manager = NotificationManagerCompat.from(context)

    @Volatile
    override var hideOnLockScreen: Boolean = false

    init {
        val pinned = NotificationChannelCompat.Builder(CHANNEL_PINNED, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Pinned notes")
            .setShowBadge(false)
            .setVibrationEnabled(false)
            .setSound(null, null)
            .build()
        val quickAdd = NotificationChannelCompat.Builder(CHANNEL_QUICK_ADD, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Quick add")
            .setShowBadge(false)
            .setVibrationEnabled(false)
            .setSound(null, null)
            .build()
        manager.createNotificationChannel(pinned)
        manager.createNotificationChannel(quickAdd)
    }

    override fun pin(note: Note) = post(note)

    override fun refresh(note: Note) = post(note)

    override fun unpin(noteId: Long) = manager.cancel(notificationId(noteId))

    // The caller gates posting behind POST_NOTIFICATIONS; lint cannot see that.
    @SuppressLint("MissingPermission")
    private fun post(note: Note) {
        val visibility =
            if (hideOnLockScreen) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PUBLIC
        val notification = NotificationCompat.Builder(context, CHANNEL_PINNED)
            .setSmallIcon(R.drawable.ic_stela_pin)
            .setContentTitle(note.title)
            .setContentText(note.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.description))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(visibility)
            .addAction(0, "Edit", editIntent(note.id))
            .addAction(0, "Remove", removeIntent(note.id))
            .build()
        manager.notify(notificationId(note.id), notification)
    }

    private fun editIntent(noteId: Long): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "$DEEP_LINK_BASE/editor/$noteId".toUri(),
            context,
            MainActivity::class.java,
        )
        return PendingIntent.getActivity(context, notificationId(noteId), intent, PENDING_FLAGS)
    }

    private fun removeIntent(noteId: Long): PendingIntent {
        val intent = NotificationActionReceiver.removeIntent(context, noteId)
        return PendingIntent.getBroadcast(context, notificationId(noteId), intent, PENDING_FLAGS)
    }

    // Body tap and the New note action both open a fresh editor; View notes opens
    // the list.
    override fun buildQuickAddNotification(): android.app.Notification {
        val newNote = deepLinkActivityIntent("$DEEP_LINK_BASE/new", QUICK_ADD_NEW_REQUEST)
        return NotificationCompat.Builder(context, CHANNEL_QUICK_ADD)
            .setSmallIcon(R.drawable.ic_stela_pin)
            .setContentTitle("New Stela note")
            .setContentText("Tap to create a new note")
            .setContentIntent(newNote)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .addAction(0, "New note", newNote)
            .addAction(0, "View notes", deepLinkActivityIntent("$DEEP_LINK_BASE/list", QUICK_ADD_VIEW_REQUEST))
            .build()
    }

    private fun deepLinkActivityIntent(uri: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri(), context, MainActivity::class.java)
        return PendingIntent.getActivity(context, requestCode, intent, PENDING_FLAGS)
    }

    companion object {
        const val CHANNEL_PINNED = "pinned_notes"
        const val CHANNEL_QUICK_ADD = "quick_add"
        const val DEEP_LINK_BASE = "stela://stela"

        // Reserved id outside the note-id space (note ids are positive, from 1).
        const val QUICK_ADD_NOTIFICATION_ID = -1

        private const val QUICK_ADD_NEW_REQUEST = -2
        private const val QUICK_ADD_VIEW_REQUEST = -3
        private const val PENDING_FLAGS =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
