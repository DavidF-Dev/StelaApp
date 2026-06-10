package dev.davidfdev.stela.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.davidfdev.stela.MainActivity
import dev.davidfdev.stela.R
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutFlowTest {

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
    fun settingsToAbout_showsVersionAndAuthor() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        // Scroll the row into view first: it sits below the Settings fold, and performClick does not auto-scroll.
        composeRule.onNodeWithText("About Stela").performScrollTo().performClick()

        // Derive the expected text from the build so the test never needs a version bump.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        composeRule.onNodeWithText(context.getString(R.string.about_version, version)).assertIsDisplayed()
        composeRule.onNodeWithText("by David F Dev").assertIsDisplayed()
        composeRule.onNodeWithText("View source").assertIsDisplayed()
    }
}
