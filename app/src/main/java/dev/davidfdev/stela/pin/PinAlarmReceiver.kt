package dev.davidfdev.stela.pin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.davidfdev.stela.StelaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/// Receives a note's auto-pin / auto-unpin alarms (not the UI). On fire it routes through [NotePinner] —
/// pin if the note is unpinned, unpin if it is pinned, otherwise a no-op — then clears the spent time.
/// Robust to a cold process start via goAsync.
class PinAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, INVALID_ID).takeIf { it != INVALID_ID } ?: return
        val action = intent.action ?: return
        val container = (context.applicationContext as StelaApp).container

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val note = container.noteRepository.getById(noteId) ?: return@launch
                when (action) {
                    ACTION_PIN -> {
                        if (!note.isPinned && !note.isArchived) container.notePinner.pin(note)
                        container.noteRepository.setSchedule(noteId, pinAt = null, unpinAt = note.unpinAt)
                    }
                    ACTION_UNPIN -> {
                        if (note.isPinned) container.notePinner.unpin(noteId)
                        container.noteRepository.setSchedule(noteId, pinAt = note.pinAt, unpinAt = null)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PIN = "dev.davidfdev.stela.action.SCHEDULED_PIN"
        const val ACTION_UNPIN = "dev.davidfdev.stela.action.SCHEDULED_UNPIN"
        private const val EXTRA_NOTE_ID = "noteId"
        private const val INVALID_ID = -1L

        fun intent(context: Context, action: String, noteId: Long): Intent =
            Intent(context, PinAlarmReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_NOTE_ID, noteId)
            }
    }
}
