package dev.davidfdev.stela.pin

import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.notifications.NotificationController
import dev.davidfdev.stela.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/// Coordinates a pin state change: persist the flag, post or cancel the
/// notification, then reconcile the service. The single seam through which
/// pin/unpin flows, and the one place that decides whether the service runs.
class NotePinner(
    private val repository: NoteRepository,
    private val controller: NotificationController,
    private val serviceController: ServiceController,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun pin(note: Note) = pinAll(listOf(note))

    suspend fun unpin(noteId: Long) = unpinAll(listOf(noteId))

    /// Deletes a note, first cancelling its notification if it was pinned. The seam
    /// through which all deletes flow so a pinned note never leaves an orphaned
    /// notification behind.
    suspend fun delete(note: Note) = deleteAll(listOf(note))

    /// Pins every note, posting each notification, then reconciles the service once
    /// for the whole batch.
    suspend fun pinAll(notes: List<Note>) {
        notes.forEach { note ->
            repository.setPinned(note.id, true)
            controller.pin(note.copy(isPinned = true))
        }
        reconcileService()
    }

    /// Unpins every id, cancelling each notification, then reconciles the service once.
    suspend fun unpinAll(noteIds: List<Long>) {
        noteIds.forEach { id ->
            repository.setPinned(id, false)
            controller.unpin(id)
        }
        reconcileService()
    }

    /// Deletes every note, cancelling the notifications of pinned ones, then
    /// reconciles the service once for the whole batch.
    suspend fun deleteAll(notes: List<Note>) {
        notes.forEach { note ->
            if (note.isPinned) controller.unpin(note.id)
            repository.delete(note)
        }
        reconcileService()
    }

    /// Re-posts a pinned note's notification so it reflects edited content. A no-op
    /// when the note is not pinned.
    fun refresh(note: Note) {
        if (note.isPinned) controller.refresh(note)
    }

    /// Re-posts every pinned note's notification — used after a preference change
    /// (e.g. lock-screen visibility) so it applies to already-posted notifications.
    suspend fun reassertPinned() {
        repository.notes.first().filter { it.isPinned }.forEach { controller.pin(it) }
    }

    /// Starts or stops the service per the lifecycle rule. Public so a settings
    /// change or a just-granted permission can re-evaluate it.
    suspend fun reconcileService() {
        val quickAddEnabled = settingsRepository.settings.first().quickAddEnabled
        if (ServiceLifecycle.shouldRun(repository.countPinned(), quickAddEnabled)) {
            serviceController.start()
        } else {
            serviceController.stop()
        }
    }
}
