package dev.davidfdev.stela.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.davidfdev.stela.MainActivity
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    companion object {
        // Granted before the Activity launches so first-run onboarding never raises the
        // POST_NOTIFICATIONS dialog, which would pause the Activity and hide its compose tree.
        @BeforeClass
        @JvmStatic
        fun grantNotificationPermission() {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    @Test
    fun longPress_entersSelectionMode_thenBatchDeleteRemovesNote() {
        val title = "Select ${System.currentTimeMillis()}"

        composeRule.onNodeWithContentDescription("New note").performClick()
        composeRule.onNodeWithText("Title").performTextInput(title)
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(title).performTouchInput { longClick() }

        // Contextual bar appears with the close affordance and the selected count.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Close selection").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Close selection").assertIsDisplayed()
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete note?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Delete").onFirst().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun selectAll_selectsEveryNote_thenBatchDeleteRemovesThem() {
        val stamp = System.currentTimeMillis()
        val first = "All A $stamp"
        val second = "All B $stamp"
        createNote(first)
        createNote(second)

        // Long-press one note: with another note still unselected, the toggle offers Select all.
        composeRule.onNodeWithText(first).performTouchInput { longClick() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Select all").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Select all").performClick()
        // With everything selected the toggle flips to Deselect all.
        composeRule.onNodeWithContentDescription("Deselect all").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onAllNodesWithText("Delete").onFirst().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(first).fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText(second).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun createNote(title: String) {
        composeRule.onNodeWithContentDescription("New note").performClick()
        composeRule.onNodeWithText("Title").performTextInput(title)
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
