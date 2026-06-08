package dev.davidfdev.stela.notifications

/// Maps a note to its notification id. Deterministic so a note always re-posts to
/// the same notification. Note ids autogenerate from 1 upward, occupying the low
/// positive ints; Phase 4's service notification reserves an id outside that space.
fun notificationId(noteId: Long): Int = noteId.toInt()
