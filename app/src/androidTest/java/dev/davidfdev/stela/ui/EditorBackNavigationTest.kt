package dev.davidfdev.stela.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.davidfdev.stela.MainActivity
import org.junit.Assert.assertFalse
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorBackNavigationTest {

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

    // The editor opened in-app (not from a notification) returns to the list on back,
    // without finishing the task.
    @Test
    fun systemBack_fromInAppEditor_returnsToListWithoutFinishing() {
        composeRule.onNodeWithContentDescription("New note").performClick()
        composeRule.onNodeWithText("Title").assertIsDisplayed()

        // A new note auto-focuses the title and raises the soft keyboard; close it first so the single
        // back press reaches the editor's BackHandler instead of only dismissing the keyboard.
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("New note").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("New note").assertIsDisplayed()
        assertFalse(composeRule.activity.isFinishing)
    }
}
