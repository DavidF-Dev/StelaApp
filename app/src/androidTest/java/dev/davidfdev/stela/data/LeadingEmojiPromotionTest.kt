package dev.davidfdev.stela.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/// Exercises [promoteLeadingEmoji] over the *real* emoji detection ([emojiRangesOf] → vanniktech), which
/// `StelaApp.onCreate` installs in the app process. Complements the by-hand-ranges unit tests by checking
/// that the live emoji set classifies graphemes as expected (keycaps vs. bare digits, ZWJ sequences).
@RunWith(AndroidJUnit4::class)
class LeadingEmojiPromotionTest {

    private fun promote(title: String, emoji: String = "") =
        promoteLeadingEmoji(title, emoji, emojiRangesOf(title))

    @Test
    fun promotesSpacedLeadingEmoji() {
        assertEquals(EmojiPromotion("👑", "Foo"), promote("👑 Foo"))
    }

    @Test
    fun promotesEmojiWithNoFollowingSpace() {
        assertEquals(EmojiPromotion("👑", "Foo"), promote("👑Foo"))
    }

    @Test
    fun keepsTitleWhenNextCharacterIsAnEmoji() {
        assertNull(promote("👑👸 Foo"))
    }

    @Test
    fun spacedSecondEmojiStaysInTitle() {
        assertEquals(EmojiPromotion("👑", "👸 Foo"), promote("👑 👸 Foo"))
    }

    @Test
    fun keycapIsAnEmojiButBareDigitIsNot() {
        assertEquals(EmojiPromotion("1️⃣", "Tasks"), promote("1️⃣ Tasks"))
        assertNull(promote("1. Buy milk"))
    }

    @Test
    fun zwjSequenceIsASingleEmoji() {
        assertEquals(EmojiPromotion("👨‍👩‍👧", "Trip"), promote("👨‍👩‍👧 Trip"))
    }

    @Test
    fun leadingWhitespaceIsTheOptOut() {
        assertNull(promote(" 👑 Foo"))
    }

    @Test
    fun doesNotPromoteWhenAnEmojiIsAlreadyChosen() {
        assertNull(promote("👑 Foo", emoji = "🛒"))
    }
}
