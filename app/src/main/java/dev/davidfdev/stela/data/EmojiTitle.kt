package dev.davidfdev.stela.data

import com.vanniktech.emoji.emojiInformation

/// A leading emoji promoted out of a title: the [emoji] and the [title] with that emoji (and the
/// whitespace that followed it) removed.
data class EmojiPromotion(val emoji: String, val title: String)

/// The UTF-16 index ranges of the emoji in [text], in order, from the app's emoji set; each range spans a
/// whole emoji grapheme. Requires `EmojiManager` to be installed (done in the application's onCreate).
fun emojiRangesOf(text: String): List<IntRange> =
    text.emojiInformation().emojiRanges.map { it.range }

/// Promotes a single leading emoji out of [title] into the emoji slot, returning the promoted emoji and the
/// trimmed remainder — or null when no promotion applies (the title is then saved unchanged). [emojiRanges]
/// are the emoji index ranges within [title] (see [emojiRangesOf]). Applies only when [currentEmoji] is
/// blank, the title begins at index 0 with an emoji that is not immediately followed by another emoji, and
/// the remainder is not blank. A leading emoji that is *not* at index 0 (e.g. after a space) stays in the
/// title — a deliberate opt-out.
fun promoteLeadingEmoji(title: String, currentEmoji: String, emojiRanges: List<IntRange>): EmojiPromotion? {
    if (currentEmoji.isNotBlank()) return null
    val lead = emojiRanges.firstOrNull { it.first == 0 } ?: return null
    val after = lead.last + 1
    if (emojiRanges.any { it.first == after }) return null
    val rest = title.substring(after).trim()
    if (rest.isEmpty()) return null
    return EmojiPromotion(emoji = title.substring(0, after), title = rest)
}
