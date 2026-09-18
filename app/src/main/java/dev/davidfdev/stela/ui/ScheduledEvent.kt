package dev.davidfdev.stela.ui

import dev.davidfdev.stela.data.Note

/// A pending auto-pin / auto-unpin, ready to display. [isUnpin] is true when the note is pinned and will
/// auto-unpin; false when it is unpinned and will auto-pin.
data class ScheduledEvent(val atMillis: Long, val isUnpin: Boolean)

/// The next scheduled event implied by a pin state and a schedule, or null when there is none. The editor
/// constrains the data so the next event is unambiguous: a pinned note can only carry an `unpinAt`, and an
/// unpinned note's next event is its `pinAt` (a snooze is also stored as a `pinAt`, so it reads as a pin).
/// Anomalous combinations the editor prevents fall through to null. Takes loose fields rather than a note
/// so an editor's unsaved state resolves by the same rule as a stored row.
fun scheduledEvent(isPinned: Boolean, pinAt: Long?, unpinAt: Long?): ScheduledEvent? = when {
    isPinned && unpinAt != null -> ScheduledEvent(unpinAt, isUnpin = true)
    !isPinned && pinAt != null -> ScheduledEvent(pinAt, isUnpin = false)
    else -> null
}

/// The note's next scheduled event, or null when it has none. See [scheduledEvent].
fun Note.scheduledEvent(): ScheduledEvent? = scheduledEvent(isPinned, pinAt, unpinAt)
