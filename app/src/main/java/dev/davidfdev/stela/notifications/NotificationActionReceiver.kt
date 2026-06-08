package dev.davidfdev.stela.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.davidfdev.stela.StelaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/// Receives the Remove action's PendingIntent — deliberately not the UI — and
/// unpins the note (the note itself is not deleted).
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMOVE) return
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, INVALID_ID)
        if (noteId == INVALID_ID) return

        val pinner = (context.applicationContext as StelaApp).container.notePinner
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                pinner.unpin(noteId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_REMOVE = "dev.davidfdev.stela.action.REMOVE"
        private const val EXTRA_NOTE_ID = "noteId"
        private const val INVALID_ID = -1L

        fun removeIntent(context: Context, noteId: Long): Intent =
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_REMOVE
                putExtra(EXTRA_NOTE_ID, noteId)
            }
    }
}
