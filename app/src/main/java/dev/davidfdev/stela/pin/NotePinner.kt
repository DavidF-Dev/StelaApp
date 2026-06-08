package dev.davidfdev.stela.pin

import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.notifications.NotificationController

/// Coordinates a pin state change: persist the flag, post or cancel the
/// notification, then reconcile the service. The single seam through which
/// pin/unpin flows, and the one place that decides whether the service runs.
class NotePinner(
    private val repository: NoteRepository,
    private val controller: NotificationController,
    private val serviceController: ServiceController,
) {
    suspend fun pin(note: Note) {
        repository.setPinned(note.id, true)
        controller.pin(note.copy(isPinned = true))
        reconcileService()
    }

    suspend fun unpin(noteId: Long) {
        repository.setPinned(noteId, false)
        controller.unpin(noteId)
        reconcileService()
    }

    /// Re-posts a pinned note's notification so it reflects edited content. A no-op
    /// when the note is not pinned.
    fun refresh(note: Note) {
        if (note.isPinned) controller.refresh(note)
    }

    // Quick-add has no independent toggle until Phase 5, so the service runs purely
    // on whether any note is pinned.
    private suspend fun reconcileService() {
        if (ServiceLifecycle.shouldRun(repository.countPinned(), quickAddEnabled = false)) {
            serviceController.start()
        } else {
            serviceController.stop()
        }
    }
}
