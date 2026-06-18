package dev.davidfdev.stela

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareEntryTest {

    @Test
    fun sendPlainText_isRecognised() {
        assertTrue(isSendTextIntent(Intent.ACTION_SEND, type = "text/plain"))
    }

    @Test
    fun sendNonText_isNotRecognised() {
        assertFalse(isSendTextIntent(Intent.ACTION_SEND, type = "image/png"))
    }

    @Test
    fun sendMissingType_isNotRecognised() {
        assertFalse(isSendTextIntent(Intent.ACTION_SEND, type = null))
    }

    @Test
    fun nonSendAction_isNotRecognised() {
        assertFalse(isSendTextIntent(Intent.ACTION_VIEW, type = "text/plain"))
    }

    @Test
    fun draft_mapsSubjectToTitleAndTextToDescription() {
        val draft = sharedNoteDraft(subject = "A page", text = "Some body")

        assertEquals("A page", draft.title)
        assertEquals("Some body", draft.description)
        assertEquals("", draft.emoji)
        assertNull(draft.noteId)
        assertTrue(draft.pinOnSave)
    }

    @Test
    fun draft_trimsSurroundingWhitespace() {
        val draft = sharedNoteDraft(subject = "  title  ", text = "\n  body \n")

        assertEquals("title", draft.title)
        assertEquals("body", draft.description)
    }

    @Test
    fun draft_missingSubject_shortText_goesToTitle() {
        val draft = sharedNoteDraft(subject = null, text = "just a body")

        assertEquals("just a body", draft.title)
        assertEquals("", draft.description)
    }

    @Test
    fun draft_missingSubject_multiLine_splitsAtFirstLine() {
        val draft = sharedNoteDraft(subject = null, text = "First line\nSecond line")

        assertEquals("First line", draft.title)
        assertEquals("Second line", draft.description)
    }

    @Test
    fun draft_bothMissing_isBlankNewNote() {
        val draft = sharedNoteDraft(subject = null, text = null)

        assertEquals("", draft.title)
        assertEquals("", draft.description)
        assertTrue(draft.pinOnSave)
    }

    @Test
    fun split_shortSingleLine_allTitle() {
        val (title, description) = splitSharedText("Buy milk")
        assertEquals("Buy milk", title)
        assertEquals("", description)
    }

    @Test
    fun split_multiLine_shortFirstLine_splitsAtNewline() {
        val (title, description) = splitSharedText("Shopping list\nMilk\nEggs\nBread")
        assertEquals("Shopping list", title)
        assertEquals("Milk\nEggs\nBread", description)
    }

    @Test
    fun split_longSingleLine_truncatesTitle_fullDescription() {
        val long = "A".repeat(100)
        val (title, description) = splitSharedText(long)
        assertEquals("A".repeat(50) + "…", title)
        assertEquals(long, description)
    }

    @Test
    fun split_multiLine_longFirstLine_truncatesTitle_fullDescription() {
        val longFirst = "B".repeat(100)
        val rest = "Second line"
        val full = "$longFirst\n$rest"
        val (title, description) = splitSharedText(full)
        assertEquals("B".repeat(50) + "…", title)
        assertEquals(full, description)
    }

    @Test
    fun split_firstLineExactly80_usesAsTitle() {
        val line = "C".repeat(80)
        val (title, description) = splitSharedText("$line\nMore")
        assertEquals(line, title)
        assertEquals("More", description)
    }

    @Test
    fun split_firstLine81_truncates() {
        val line = "D".repeat(81)
        val (title, description) = splitSharedText("$line\nMore")
        assertEquals("D".repeat(50) + "…", title)
        assertEquals("$line\nMore", description)
    }

    @Test
    fun split_trimsFirstLineTrailingSpaces() {
        val (title, description) = splitSharedText("Title with spaces   \nBody")
        assertEquals("Title with spaces", title)
        assertEquals("Body", description)
    }

    @Test
    fun split_trimsDescriptionLeadingNewlines() {
        val (title, description) = splitSharedText("Title\n\n\nBody after blanks")
        assertEquals("Title", title)
        assertEquals("Body after blanks", description)
    }

    @Test
    fun split_truncation_trimsTrailingSpaceBeforeEllipsis() {
        val text = "word ".repeat(20)
        val (title, _) = splitSharedText(text)
        assertEquals("word word word word word word word word word word…", title)
    }
}
