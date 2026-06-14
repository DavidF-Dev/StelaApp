package dev.davidfdev.stela.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.davidfdev.stela.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {

    // MainActivity hosts the onboarding gate; an empty rule lets us force first-run state before launch.
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun forceFirstRun() {
        setOnboardingComplete(false)
    }

    @After
    fun restore() {
        // Leave the device in the "onboarded" state so it can't gate other MainActivity tests.
        setOnboardingComplete(true)
    }

    @Test
    fun firstLaunch_showsOnboarding_thenSkipReachesNoteList() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // The welcome pane is the proof the gate showed onboarding rather than the list.
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("Notes as notifications").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Notes as notifications").assertIsDisplayed()

            // Skip completes onboarding and lands on the note list (its FAB).
            composeRule.onNodeWithText("Skip").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription("New note").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("New note").assertIsDisplayed()
        }
    }

    companion object {
        // Granted so the notifications pane never raises the system dialog, which would pause the activity.
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
