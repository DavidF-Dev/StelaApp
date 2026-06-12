package dev.davidfdev.stela.pin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.content.getSystemService
import dev.davidfdev.stela.notifications.notificationId

/// The production [PinScheduler]: posts inexact, allow-while-idle `AlarmManager` alarms that fire a
/// [PinAlarmReceiver] broadcast around the chosen time. Inexact + allow-while-idle needs no exact-alarm
/// permission and still fires under Doze; precise timing is traded for the clean permission story.
class AlarmPinScheduler(private val context: Context) : PinScheduler {

    private val alarmManager = context.getSystemService<AlarmManager>()

    override fun schedulePin(noteId: Long, atMillis: Long) =
        set(noteId, atMillis, PinAlarmReceiver.ACTION_PIN)

    override fun scheduleUnpin(noteId: Long, atMillis: Long) =
        set(noteId, atMillis, PinAlarmReceiver.ACTION_UNPIN)

    override fun cancelPin(noteId: Long) = cancel(noteId, PinAlarmReceiver.ACTION_PIN)

    override fun cancelUnpin(noteId: Long) = cancel(noteId, PinAlarmReceiver.ACTION_UNPIN)

    private fun set(noteId: Long, atMillis: Long, action: String) {
        alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(noteId, action))
    }

    private fun cancel(noteId: Long, action: String) {
        alarmManager?.cancel(pendingIntent(noteId, action))
    }

    // The pin and unpin intents for one note share its request code but differ by action, so they map to
    // distinct PendingIntents (action is part of PendingIntent equality; the note-id extra is not).
    private fun pendingIntent(noteId: Long, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            notificationId(noteId),
            PinAlarmReceiver.intent(context, action, noteId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
