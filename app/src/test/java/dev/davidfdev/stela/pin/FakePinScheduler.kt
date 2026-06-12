package dev.davidfdev.stela.pin

/// Records the auto-pin / auto-unpin alarms a test scheduled or cancelled, keyed by note id, so an
/// assertion can check what would have been armed without touching the real AlarmManager.
class FakePinScheduler : PinScheduler {

    val scheduledPins = mutableMapOf<Long, Long>()
    val scheduledUnpins = mutableMapOf<Long, Long>()
    val cancelledPins = mutableListOf<Long>()
    val cancelledUnpins = mutableListOf<Long>()

    override fun schedulePin(noteId: Long, atMillis: Long) {
        scheduledPins[noteId] = atMillis
    }

    override fun scheduleUnpin(noteId: Long, atMillis: Long) {
        scheduledUnpins[noteId] = atMillis
    }

    override fun cancelPin(noteId: Long) {
        scheduledPins.remove(noteId)
        cancelledPins.add(noteId)
    }

    override fun cancelUnpin(noteId: Long) {
        scheduledUnpins.remove(noteId)
        cancelledUnpins.add(noteId)
    }
}
