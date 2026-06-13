package dev.davidfdev.stela.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/// Pure-logic tests for [promoteLeadingEmoji]; emoji ranges are supplied by hand (UTF-16 offsets), so no
/// `EmojiManager` is needed. 👑/👸 are surrogate pairs (two UTF-16 units each); 1️⃣ is digit + U+FE0F + U+20E3.
class EmojiTitleTest {

    @Test
    fun promotesSpacedLeadingEmoji() {
        assertEquals(
            EmojiPromotion(emoji = "👑", title = "Foo"),
            promoteLeadingEmoji("👑 Foo", "", listOf(0..1)),
        )
    }

    @Test
    fun promotesEmojiWithNoFollowingSpace() {
        assertEquals(
            EmojiPromotion(emoji = "👑", title = "Foo"),
            promoteLeadingEmoji("👑Foo", "", listOf(0..1)),
        )
    }

    @Test
    fun keepsTitleWhenNextCharacterIsAnEmoji() {
        // 👑👸 Foo: ranges 0..1 and 2..3 — the char after the leading emoji is itself an emoji.
        assertNull(promoteLeadingEmoji("👑👸 Foo", "", listOf(0..1, 2..3)))
    }

    @Test
    fun spacedSecondEmojiStaysInTitle() {
        // 👑 👸 Foo: 👑=0..1, 👸=3..4 — the char after 👑 is a space, so 👑 promotes and 👸 stays.
        assertEquals(
            EmojiPromotion(emoji = "👑", title = "👸 Foo"),
            promoteLeadingEmoji("👑 👸 Foo", "", listOf(0..1, 3..4)),
        )
    }

    @Test
    fun doesNotPromoteWhenAnEmojiIsAlreadyChosen() {
        assertNull(promoteLeadingEmoji("👑 Foo", "🛒", listOf(0..1)))
    }

    @Test
    fun ignoresEmojiThatIsNotAtTheStart() {
        assertNull(promoteLeadingEmoji("Foo 👑", "", listOf(4..5)))
    }

    @Test
    fun leadingWhitespaceIsTheOptOut() {
        // " 👑 Foo": 👑 sits at 1..2, not index 0 — left in the title on purpose.
        assertNull(promoteLeadingEmoji(" 👑 Foo", "", listOf(1..2)))
    }

    @Test
    fun keepsTitleWhenRemainderIsBlank() {
        assertNull(promoteLeadingEmoji("👑", "", listOf(0..1)))
        assertNull(promoteLeadingEmoji("👑   ", "", listOf(0..1)))
    }

    @Test
    fun trimsEndsButPreservesInternalWhitespace() {
        assertEquals(
            EmojiPromotion(emoji = "👑", title = "Foo  Bar"),
            promoteLeadingEmoji("👑  Foo  Bar", "", listOf(0..1)),
        )
    }

    @Test
    fun promotesMultiCodepointKeycap() {
        // 1️⃣ is three UTF-16 units (0..2); "Tasks" follows after a space.
        assertEquals(
            EmojiPromotion(emoji = "1️⃣", title = "Tasks"),
            promoteLeadingEmoji("1️⃣ Tasks", "", listOf(0..2)),
        )
    }

    @Test
    fun keepsTitleWhenNoEmojiPresent() {
        assertNull(promoteLeadingEmoji("Buy milk", "", emptyList()))
    }
}
