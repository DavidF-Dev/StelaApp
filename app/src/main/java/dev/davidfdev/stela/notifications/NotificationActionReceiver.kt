package dev.davidfdev.stela.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.davidfdev.stela.StelaApp
import dev.davidfdev.stela.pin.ServiceLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/// Receives notification PendingIntents — deliberately not the UI. Handles Unpin (the
/// note is kept, not deleted), Reassert (re-post a swiped pin), and ReassertService
/// (re-post the swiped foreground-service notification) — the self-heals triggered by a
/// notification's deleteIntent.
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as StelaApp).container

        when (intent.action) {
            ACTION_UNPIN -> {
                val noteId = intent.noteId() ?: return
                runAsync { container.notePinner.unpin(noteId) }
            }

            ACTION_ARCHIVE -> {
                val noteId = intent.noteId() ?: return
                runAsync {
                    container.noteRepository.getById(noteId)?.let { container.notePinner.archive(it) }
                }
            }

            ACTION_REASSERT -> {
                val noteId = intent.noteId() ?: return
                runAsync {
                    // Only re-post if still pinned; an unpinned/removed note must stay gone.
                    container.noteRepository.getById(noteId)
                        ?.takeIf { it.isPinned }
                        ?.let { container.notificationController.pin(it) }
                }
            }

            ACTION_REASSERT_SERVICE -> runAsync {
                // Re-post only while the service should still be running.
                val quickAddEnabled = container.settingsRepository.settings.first().quickAddEnabled
                if (ServiceLifecycle.shouldRun(container.noteRepository.countPinned(), quickAddEnabled)) {
                    container.notificationController.reassertServiceNotification(quickAddEnabled)
                }
            }
        }
    }

    /// Runs suspending work past onReceive's return by holding the broadcast alive.
    private fun runAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    private fun Intent.noteId(): Long? = getLongExtra(EXTRA_NOTE_ID, INVALID_ID).takeIf { it != INVALID_ID }

    companion object {
        private const val ACTION_UNPIN = "dev.davidfdev.stela.action.UNPIN"
        private const val ACTION_ARCHIVE = "dev.davidfdev.stela.action.ARCHIVE"
        private const val ACTION_REASSERT = "dev.davidfdev.stela.action.REASSERT"
        private const val ACTION_REASSERT_SERVICE = "dev.davidfdev.stela.action.REASSERT_SERVICE"
        private const val EXTRA_NOTE_ID = "noteId"
        private const val INVALID_ID = -1L

        fun unpinIntent(context: Context, noteId: Long): Intent =
            actionIntent(context, ACTION_UNPIN, noteId)

        fun archiveIntent(context: Context, noteId: Long): Intent =
            actionIntent(context, ACTION_ARCHIVE, noteId)

        fun reassertIntent(context: Context, noteId: Long): Intent =
            actionIntent(context, ACTION_REASSERT, noteId)

        fun reassertServiceIntent(context: Context): Intent =
            Intent(context, NotificationActionReceiver::class.java).apply { action = ACTION_REASSERT_SERVICE }

        private fun actionIntent(context: Context, action: String, noteId: Long): Intent =
            Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_NOTE_ID, noteId)
            }
    }
}
