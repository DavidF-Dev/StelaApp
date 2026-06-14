package dev.davidfdev.stela.notifications

// A compact cap so a long note can't fill the shade; the full note is a tap away.
private const val MAX_BODY_LENGTH = 140

// How far back from the cap we'll look for a space to avoid cutting mid-word.
private const val WORD_BREAK_WINDOW = 40

/// The note description as shown in the expanded pinned notification: returned unchanged
/// when within the cap, otherwise cut to a compact length — on a nearby word boundary when
/// one exists, else a hard cut — with an ellipsis appended.
fun notificationBody(description: String): String {
    if (description.length <= MAX_BODY_LENGTH) return description
    val window = description.substring(0, MAX_BODY_LENGTH)
    val lastWhitespace = window.indexOfLast { it.isWhitespace() }
    val cut = if (lastWhitespace >= MAX_BODY_LENGTH - WORD_BREAK_WINDOW) lastWhitespace else MAX_BODY_LENGTH
    return description.substring(0, cut).trimEnd() + "…"
}
