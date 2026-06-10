package dev.davidfdev.stela.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayTitleTest {

    @Test
    fun noEmoji_returnsTitleOnly() {
        assertEquals("Buy milk", displayTitle(emoji = "", title = "Buy milk"))
    }

    @Test
    fun blankEmoji_returnsTitleOnly() {
        assertEquals("Buy milk", displayTitle(emoji = "   ", title = "Buy milk"))
    }

    @Test
    fun withEmoji_prependsWithSpace() {
        assertEquals("🛒 Buy milk", displayTitle(emoji = "🛒", title = "Buy milk"))
    }

    @Test
    fun noteExtension_usesItsOwnEmojiAndTitle() {
        val note = Note(title = "Buy milk", description = "", emoji = "🛒", createdAt = 0, updatedAt = 0)
        assertEquals("🛒 Buy milk", note.displayTitle)
    }
}
