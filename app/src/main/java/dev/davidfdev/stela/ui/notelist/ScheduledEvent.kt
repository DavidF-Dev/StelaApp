package dev.davidfdev.stela.ui.notelist

import dev.davidfdev.stela.data.Note

/// A note's next scheduled pin/unpin, for the list's schedule indicator. [isUnpin] is true when the note
/// is pinned and will auto-unpin; false when it is unpinned and will auto-pin.
data class ScheduledEvent(val atMillis: Long, val isUnpin: Boolean)

/// The note's next scheduled event, or null when it has none. The editor constrains the data so the next
/// event is unambiguous: a pinned note can only carry an `unpinAt`, and an unpinned note's next event is
/// its `pinAt` (a snooze is also stored as a `pinAt`, so it reads as a pin). Anomalous combinations the
/// editor prevents fall through to null.
fun Note.scheduledEvent(): ScheduledEvent? = when {
    isPinned && unpinAt != null -> ScheduledEvent(unpinAt, isUnpin = true)
    !isPinned && pinAt != null -> ScheduledEvent(pinAt, isUnpin = false)
    else -> null
}
