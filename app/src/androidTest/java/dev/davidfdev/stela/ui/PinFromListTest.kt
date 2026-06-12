package dev.davidfdev.stela.ui

import android.Manifest
import android.app.NotificationManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.davidfdev.stela.MainActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinFromListTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val manager by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(NotificationManager::class.java)
    }

    @Before
    fun grantNotificationPermission() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            InstrumentationRegistry.getInstrumentation().targetContext.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    @After
    fun tearDown() {
        manager.cancelAll()
    }

    @Test
    fun pinningFromList_flipsIconAndPostsNotification() {
        val title = "Pin ${System.currentTimeMillis()}"

        composeRule.onNodeWithContentDescription("New note").performClick()
        composeRule.onNodeWithText("Title").performTextInput(title)
        // New notes default to pinned; create this one unpinned so the test can pin it from the list.
        composeRule.onNodeWithContentDescription("Unpin").performClick()
        composeRule.onNodeWithContentDescription("Save").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        // The newly created note is the most-recently-updated, so it is the top row.
        composeRule.onAllNodesWithContentDescription("Pin").onFirst().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Unpin").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription("Unpin").onFirst().assertIsDisplayed()

        waitForNotification()
        assertTrue(manager.activeNotifications.isNotEmpty())
    }

    private fun waitForNotification(timeoutMs: Long = 5_000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (manager.activeNotifications.isNotEmpty()) return
            Thread.sleep(50)
        }
    }
}
