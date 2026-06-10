package dev.davidfdev.stela.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.davidfdev.stela.MainActivity
import dev.davidfdev.stela.StelaApp
import dev.davidfdev.stela.settings.NoteFilter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListQueryFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    companion object {
        @BeforeClass
        @JvmStatic
        fun grantNotificationPermission() {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    // Sort/filter persist to the real DataStore; reset after each test so a mutated (or
    // interrupted) filter can't hide notes from the other tests on this device.
    @After
    fun resetPersistedFilter() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StelaApp
        runBlocking { app.container.settingsRepository.setNoteFilter(NoteFilter.ALL) }
    }

    @Test
    fun search_narrowsListToMatchingNotes() {
        val unique = System.currentTimeMillis()
        val apple = "Apple $unique"
        val banana = "Banana $unique"
        createNote(apple)
        createNote(banana)

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Search").performClick()
        // Type the bare word so the field's text differs from the matching row's full title.
        composeRule.onNodeWithText("Search notes").performTextInput("Apple")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(banana).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText(apple).assertIsDisplayed()
    }

    @Test
    fun pinnedFilter_hidesUnpinnedNote_thenRestored() {
        val unique = System.currentTimeMillis()
        val plain = "Plain $unique"
        createNote(plain)
        composeRule.onNodeWithText(plain).assertIsDisplayed()

        // Filter to Pinned: the freshly-created (unpinned) note disappears.
        setFilter("Pinned")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(plain).fetchSemanticsNodes().isEmpty()
        }

        // Restore to All (the filter persists, so leave it at the default for other tests).
        setFilter("All")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(plain).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /// Opens the sort/filter sheet, taps an option, and dismisses it — waiting for the
    /// sheet to fully open and close so taps never race the animation.
    private fun setFilter(option: String) {
        composeRule.onNodeWithContentDescription("Sort and filter").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Show").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(option).performClick()
        Espresso.pressBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Show").fetchSemanticsNodes().isEmpty()
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
