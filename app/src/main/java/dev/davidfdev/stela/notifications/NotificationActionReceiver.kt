package dev.davidfdev.stela.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.davidfdev.stela.StelaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/// Receives pinned-notification PendingIntents — deliberately not the UI. Handles
/// Remove (unpin; the note is not deleted) and Reassert (re-post a swiped pin, the
/// self-heal triggered by the notification's deleteIntent).
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, INVALID_ID)
        if (noteId == INVALID_ID) return
        val container = (context.applicationContext as StelaApp).container

        when (intent.action) {
            ACTION_REMOVE -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        container.notePinner.unpin(noteId)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_REASSERT -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        // Only re-post if still pinned; an unpinned/removed note must stay gone.
                        container.noteRepository.getById(noteId)
                            ?.takeIf { it.isPinned }
                            ?.let { container.notificationController.pin(it) }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val ACTION_REMOVE = "dev.davidfdev.stela.action.REMOVE"
        private const val ACTION_REASSERT = "dev.davidfdev.stela.action.REASSERT"
        private const val EXTRA_NOTE_ID = "noteId"
        private const val INVALID_ID = -1L

        fun removeIntent(context: Context, noteId: Long): Intent =
            actionIntent(context, ACTION_REMOVE, noteId)

        fun reassertIntent(context: Context, noteId: Long): Intent =
            actionIntent(context, ACTION_REASSERT, noteId)

        private fun actionIntent(context: Context, action: String, noteId: Long): Intent =
            Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_NOTE_ID, noteId)
            }
    }
}
