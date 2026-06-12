package dev.davidfdev.stela.ui

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.davidfdev.stela.ui.shortcut.NewNoteShortcutActivity
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewNoteShortcutActivityTest {

    // The trampoline forwards to (and the popup hosts) the Compose hierarchy; an empty rule hooks into it.
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun launchingShortcut_opensQuickNotePopup() {
        context.startActivity(
            Intent(context, NewNoteShortcutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        // The forwarded popup's Title field is the visible proof the trampoline reached QuickNoteActivity.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Title").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Title").assertIsDisplayed()
    }

    @After
    fun closePopup() {
        // The trampoline finished itself; finish the popup it launched so it can't poison later tests.
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .forEach { it.finish() }
        }
    }

    companion object {
        // Granted before launch so save-time pinning is permitted and no permission dialog steals focus.
        @BeforeClass
        @JvmStatic
        fun grantNotificationPermission() {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }
}
