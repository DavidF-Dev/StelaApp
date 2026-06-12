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
    private val pinScheduler: PinScheduler = NoopPinScheduler,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun pin(note: Note) = pinAll(listOf(note))

    suspend fun unpin(noteId: Long) = unpinAll(listOf(noteId))

    /// Deletes a note, first cancelling its notification if it was pinned. The seam
    /// through which all deletes flow so a pinned note never leaves an orphaned
    /// notification behind.
    suspend fun delete(note: Note) = deleteAll(listOf(note))

    /// Pins every note, posting each notification, then reconciles the service once
    /// for the whole batch. Pinning also unarchives, since a note is never both pinned
    /// and archived, and clears any pending auto-pin (now fulfilled).
    suspend fun pinAll(notes: List<Note>) {
        notes.forEach { note ->
            repository.setArchived(note.id, false)
            repository.setPinned(note.id, true)
            controller.pin(note.copy(isPinned = true, isArchived = false))
            repository.clearPinAt(note.id)
            pinScheduler.cancelPin(note.id)
        }
        reconcileService()
    }

    /// Unpins every id, cancelling each notification and any pending auto-unpin (now
    /// fulfilled), then reconciles the service once. [snooze] deliberately bypasses this so
    /// it can keep the unpin time.
    suspend fun unpinAll(noteIds: List<Long>) {
        noteIds.forEach { id ->
            repository.setPinned(id, false)
            controller.unpin(id)
            repository.clearUnpinAt(id)
            pinScheduler.cancelUnpin(id)
        }
        reconcileService()
    }

    /// Archives a note: the reversible alternative to delete. See [archiveAll].
    suspend fun archive(note: Note) = archiveAll(listOf(note))

    /// Restores a note from the archive. See [unarchiveAll].
    suspend fun unarchive(note: Note) = unarchiveAll(listOf(note))

    /// Archives every note: unpins and cancels the notification of any that were pinned,
    /// then sets the archive flag, and reconciles the service once for the batch. Archived
    /// notes are hidden from the list and can never be pinned (pinning restores them first,
    /// see [pinAll]). The schedule and its alarms are kept but dormant: a fired timer only
    /// clears its spent time (the receiver and [PinSchedule] no-op on an archived note), so the
    /// schedule self-expires rather than going stale, and [unarchiveAll] reconciles it on restore.
    suspend fun archiveAll(notes: List<Note>) {
        notes.forEach { note ->
            if (note.isPinned) {
                // Raw unpin (not unpinAll) so archiving keeps unpinAt and the rest of the schedule.
                repository.setPinned(note.id, false)
                controller.unpin(note.id)
            }
            repository.setArchived(note.id, true)
        }
        reconcileService()
    }

    /// Restores every note from the archive (the inverse of [archiveAll]) and reconciles each against
    /// its kept schedule — future times resume, past-due ones clear, and a now-due pin catches up. The
    /// service reconciles once in case a catch-up pin started it.
    suspend fun unarchiveAll(notes: List<Note>) {
        notes.forEach { note ->
            repository.setArchived(note.id, false)
            repository.getById(note.id)?.let { reconcile(it) }
        }
        reconcileService()
    }

    /// Deletes every note, cancelling the notifications of pinned ones and any pending
    /// alarms, then reconciles the service once for the whole batch.
    suspend fun deleteAll(notes: List<Note>) {
        notes.forEach { note ->
            if (note.isPinned) controller.unpin(note.id)
            pinScheduler.cancelPin(note.id)
            pinScheduler.cancelUnpin(note.id)
            repository.delete(note)
        }
        reconcileService()
    }

    /// Restores deleted notes (the inverse of [deleteAll]): re-inserts each as it was,
    /// re-posts the notifications of the ones that were pinned, re-applies any schedule the
    /// note carried, then reconciles once.
    suspend fun restore(notes: List<Note>) {
        notes.forEach { note ->
            repository.restore(note)
            if (note.isPinned) controller.pin(note)
            reconcile(note)
        }
        reconcileService()
    }

    /// Sets a note's auto-pin / auto-unpin times and reconciles it (firing now if a time is
    /// already past, else scheduling the alarm). The seam the editor's Save routes through.
    suspend fun applySchedule(noteId: Long, pinAt: Long?, unpinAt: Long?) {
        repository.setSchedule(noteId, pinAt, unpinAt)
        repository.getById(noteId)?.let { reconcile(it) }
    }

    /// Snoozes a note: hides it from the tray now and re-pins it at [untilMillis] by reusing the auto-pin
    /// schedule. Unlike a manual [unpin] this keeps any auto-unpin time, so the note's pin window survives
    /// the snooze.
    suspend fun snooze(noteId: Long, untilMillis: Long) {
        val note = repository.getById(noteId) ?: return
        // Hide it now without clearing unpinAt (so [unpinAll]'s clear is bypassed).
        if (note.isPinned) {
            repository.setPinned(noteId, false)
            controller.unpin(noteId)
        }
        applySchedule(noteId, pinAt = untilMillis, unpinAt = note.unpinAt)
        reconcileService()
    }

    /// Reconciles every scheduled note against the current time — fires past-due transitions
    /// and re-arms future alarms. Run on boot (alarms don't survive a reboot) and app start
    /// (covers process kill and Doze-missed alarms).
    suspend fun reconcileAll() {
        repository.notes.first()
            .filter { it.pinAt != null || it.unpinAt != null }
            .forEach { reconcile(it) }
    }

    /// Snaps one note to the state its schedule implies now (see [PinSchedule]): applies the pin/unpin if
    /// it changed, persists the surviving times, and (re)arms or cancels their alarms.
    private suspend fun reconcile(note: Note) {
        val resolution = PinSchedule.resolve(note.isPinned, note.isArchived, note.pinAt, note.unpinAt, now())
        if (resolution.targetPinned != note.isPinned) {
            if (resolution.targetPinned) pin(note) else unpin(note.id)
        }
        if (resolution.pinAt != note.pinAt || resolution.unpinAt != note.unpinAt) {
            repository.setSchedule(note.id, resolution.pinAt, resolution.unpinAt)
        }
        resolution.pinAt?.let { pinScheduler.schedulePin(note.id, it) } ?: pinScheduler.cancelPin(note.id)
        resolution.unpinAt?.let { pinScheduler.scheduleUnpin(note.id, it) } ?: pinScheduler.cancelUnpin(note.id)
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
