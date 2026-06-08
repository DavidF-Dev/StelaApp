package dev.davidfdev.stela.pin

import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.notifications.NotificationController

/// Coordinates a pin state change: persist the flag, then post or cancel the
/// notification. The single seam through which pin/unpin flows — Phase 4's service
/// will hook in here.
class NotePinner(
    private val repository: NoteRepository,
    private val controller: NotificationController,
) {
    suspend fun pin(note: Note) {
        repository.setPinned(note.id, true)
        controller.pin(note.copy(isPinned = true))
    }

    suspend fun unpin(noteId: Long) {
        repository.setPinned(noteId, false)
        controller.unpin(noteId)
    }

    /// Re-posts a pinned note's notification so it reflects edited content. A no-op
    /// when the note is not pinned.
    fun refresh(note: Note) {
        if (note.isPinned) controller.refresh(note)
    }
}
