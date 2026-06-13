package dev.davidfdev.stela

import android.content.Intent
import dev.davidfdev.stela.notifications.AndroidNotificationController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorEntryTest {

    private val scheme = AndroidNotificationController.DEEP_LINK_SCHEME

    @Test
    fun editNoteDeepLink_isRecognised() {
        assertTrue(isEditorDeepLink(Intent.ACTION_VIEW, scheme, path = "/editor/42"))
    }

    @Test
    fun newNoteDeepLink_isRecognised() {
        assertTrue(isEditorDeepLink(Intent.ACTION_VIEW, scheme, path = "/new"))
    }

    @Test
    fun listDeepLink_isNotAnEditorEntry() {
        assertFalse(isEditorDeepLink(Intent.ACTION_VIEW, scheme, path = "/list"))
    }

    @Test
    fun launcherIntent_isNotAnEditorEntry() {
        assertFalse(isEditorDeepLink(Intent.ACTION_MAIN, scheme = null, path = null))
    }

    @Test
    fun viewIntentForForeignScheme_isNotAnEditorEntry() {
        assertFalse(isEditorDeepLink(Intent.ACTION_VIEW, scheme = "https", path = "/editor/42"))
    }

    @Test
    fun nullAction_isNotAnEditorEntry() {
        assertFalse(isEditorDeepLink(action = null, scheme, path = "/editor/42"))
    }
}
