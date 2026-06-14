package dev.davidfdev.stela.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.davidfdev.stela.MainActivity
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorDiscardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun back_withUnsavedEdits_confirmsBeforeLeaving() {
        composeRule.onNodeWithContentDescription("New note").performClick()
        composeRule.onNodeWithText("Title").performTextInput("Draft note")

        // Back with edits shows the discard confirm rather than leaving.
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()

        // Keep editing dismisses the dialog and stays in the editor.
        composeRule.onNodeWithText("Keep editing").performClick()
        composeRule.onNodeWithText("Discard changes?").assertDoesNotExist()
        composeRule.onNodeWithText("Title").assertIsDisplayed()

        // Back again, then Discard, leaves to the list.
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("New note").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("New note").assertIsDisplayed()
    }

    @Test
    fun back_withNoEdits_leavesWithoutConfirm() {
        composeRule.onNodeWithContentDescription("New note").performClick()
        composeRule.onNodeWithText("Title").assertIsDisplayed()

        // A pristine note has nothing to lose, so Back returns to the list with no dialog.
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Discard changes?").assertDoesNotExist()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("New note").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("New note").assertIsDisplayed()
    }

    companion object {
        // Granted before launch + onboarding marked complete so the editor is reachable and stable.
        @BeforeClass
        @JvmStatic
        fun grantNotificationPermission() {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            markOnboardingComplete()
        }
    }
}
