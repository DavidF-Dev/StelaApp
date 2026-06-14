package dev.davidfdev.stela.ui

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.davidfdev.stela.MainActivity
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareToStelaTest {

    // MainActivity hosts the Compose hierarchy; an empty rule lets us launch it with a custom share intent.
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sharingText_opensEditorPrefilled_andSaves() {
        // Unique per run so the assertions are unambiguous against the on-device db.
        val stamp = System.currentTimeMillis()
        val title = "Shared title $stamp"
        val body = "Shared body $stamp"
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(intent).use {
            // The editor opens prefilled: the shared body proves the draft was seeded into the description.
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText(body).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(body).assertIsDisplayed()

            // Saving (enabled because the subject filled the title) lands the note in the list.
            composeRule.onNodeWithContentDescription("Save").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(title).assertIsDisplayed()
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
