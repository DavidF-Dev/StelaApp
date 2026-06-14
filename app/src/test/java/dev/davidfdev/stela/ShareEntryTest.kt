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
    fun draft_missingSubject_leavesTitleEmpty() {
        val draft = sharedNoteDraft(subject = null, text = "just a body")

        assertEquals("", draft.title)
        assertEquals("just a body", draft.description)
    }

    @Test
    fun draft_bothMissing_isBlankNewNote() {
        val draft = sharedNoteDraft(subject = null, text = null)

        assertEquals("", draft.title)
        assertEquals("", draft.description)
        assertTrue(draft.pinOnSave)
    }
}
