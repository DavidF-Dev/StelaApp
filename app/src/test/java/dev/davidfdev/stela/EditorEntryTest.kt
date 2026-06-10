package dev.davidfdev.stela

import android.content.Intent
import dev.davidfdev.stela.notifications.AndroidNotificationController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorEntryTest {

    @Test
    fun notificationDeepLink_isRecognised() {
        assertTrue(isNotificationDeepLink(Intent.ACTION_VIEW, AndroidNotificationController.DEEP_LINK_SCHEME))
    }

    @Test
    fun launcherIntent_isNotADeepLink() {
        assertFalse(isNotificationDeepLink(Intent.ACTION_MAIN, scheme = null))
    }

    @Test
    fun viewIntentForForeignScheme_isNotADeepLink() {
        assertFalse(isNotificationDeepLink(Intent.ACTION_VIEW, scheme = "https"))
    }

    @Test
    fun nullAction_isNotADeepLink() {
        assertFalse(isNotificationDeepLink(action = null, scheme = AndroidNotificationController.DEEP_LINK_SCHEME))
    }
}
