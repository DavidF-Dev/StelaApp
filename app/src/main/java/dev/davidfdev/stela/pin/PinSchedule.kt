package dev.davidfdev.stela.pin

/// The pure decision for reconciling a note's auto-pin / auto-unpin schedule against the current time.
/// `pinAt` and `unpinAt` are independent one-shot timers: a time at or before `now` has fired and is
/// cleared; a future time survives and is (re)scheduled. [Resolution.targetPinned] snaps the note to its
/// correct current state, so a missed alarm (Doze, process kill, reboot) self-corrects on the next pass.
object PinSchedule {

    /// The reconciled outcome: the pin state the note should hold, and the times that remain (a fired or
    /// absent time is null). Surviving times are both the persisted schedule and what to (re)schedule.
    data class Resolution(val targetPinned: Boolean, val pinAt: Long?, val unpinAt: Long?)

    fun resolve(isPinned: Boolean, isArchived: Boolean, pinAt: Long?, unpinAt: Long?, now: Long): Resolution {
        // An archived note never carries a schedule (a note is never both archived and pinned).
        if (isArchived) return Resolution(targetPinned = isPinned, pinAt = null, unpinAt = null)

        // Apply the fired transitions in time order; the latest one not after `now` wins.
        var pinned = isPinned
        buildList {
            if (pinAt != null && pinAt <= now) add(pinAt to true)
            if (unpinAt != null && unpinAt <= now) add(unpinAt to false)
        }.sortedBy { it.first }.forEach { (_, pins) -> pinned = pins }

        return Resolution(
            targetPinned = pinned,
            pinAt = pinAt?.takeIf { it > now },
            unpinAt = unpinAt?.takeIf { it > now },
        )
    }
}
