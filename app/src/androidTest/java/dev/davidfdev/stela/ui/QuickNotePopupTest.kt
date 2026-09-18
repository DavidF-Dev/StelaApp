package dev.davidfdev.stela.ui

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.davidfdev.stela.StelaApp
import dev.davidfdev.stela.ui.quicknote.QuickNoteActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickNotePopupTest {

    // The popup launches itself via ActivityScenario, so an empty rule (not an activity-launching one)
    // hooks into the externally-launched Compose hierarchy.
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as StelaApp).container

    private fun notesNow() = runBlocking { container.noteRepository.notes.first() }

    @Test
    fun newNotePopup_save_createsPinnedNote() {
        // Unique per run so the assertion is unambiguous against the on-device db.
        val title = "Quick ${System.currentTimeMillis()}"
        val intent = QuickNoteActivity.newNoteIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<QuickNoteActivity>(intent).use {
            composeRule.onNodeWithText("Title").performTextInput(title)
            composeRule.onNodeWithContentDescription("Save").performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                notesNow().any { it.title == title }
            }
        }
        // The quick-note contract: a saved new note is pinned.
        assertTrue(notesNow().first { it.title == title }.isPinned)
    }

    @Test
    fun newNotePopup_overflowOffersShareAndSnooze() {
        val intent = QuickNoteActivity.newNoteIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<QuickNoteActivity>(intent).use {
            composeRule.onNodeWithContentDescription("More").performClick()

            // A note that has never been saved can still be shared and scheduled.
            composeRule.onNodeWithText("Share").assertIsDisplayed()
            composeRule.onNodeWithText("Snooze for", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("Snooze until", substring = true).assertIsDisplayed()

            // The menu draws in its own window; close it so none is left over the activity at teardown.
            pressSystemBack()
        }
    }

    @Test
    fun newNotePopup_snoozeThenSave_createsUnpinnedNoteWithScheduledPin() {
        val title = "Later ${System.currentTimeMillis()}"
        val intent = QuickNoteActivity.newNoteIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<QuickNoteActivity>(intent).use {
            composeRule.onNodeWithText("Title").performTextInput(title)
            composeRule.onNodeWithContentDescription("More").performClick()
            composeRule.onNodeWithText("Snooze for", substring = true).performClick()
            composeRule.onNodeWithText("30 minutes").performClick()

            // The card states the pending pin — a content check, not a claim that it is pixel-visible: it
            // sits below the description, which the raised keyboard can push out of view.
            composeRule.onNodeWithText("Pins", substring = true).assertExists()

            composeRule.onNodeWithContentDescription("Save").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                notesNow().any { it.title == title }
            }
        }

        // The snooze defers the quick note's pin-on-save rather than pinning now.
        val note = notesNow().first { it.title == title }
        assertFalse(note.isPinned)
        assertNotNull(note.pinAt)
        // Leave no armed alarm behind to re-pin this note during a later test.
        runBlocking { container.notePinner.delete(note) }
    }

    @Test
    fun newNotePopup_afterSnoozing_snoozeStaysAvailableToRetime() {
        val intent = QuickNoteActivity.newNoteIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<QuickNoteActivity>(intent).use {
            composeRule.onNodeWithContentDescription("More").performClick()
            composeRule.onNodeWithText("Snooze for", substring = true).performClick()
            composeRule.onNodeWithText("30 minutes").performClick()

            // Snoozing leaves the note unpinned, but the pin it is waiting on is still there to retime.
            composeRule.onNodeWithContentDescription("More").performClick()
            composeRule.onNodeWithText("Snooze for", substring = true).assertIsEnabled()
            composeRule.onNodeWithText("Snooze until", substring = true).assertIsEnabled()

            // The menu draws in its own window; close it so none is left over the activity at teardown.
            pressSystemBack()
        }
    }

    @Test
    fun existingNotePopup_prefillsStoredFields() {
        val title = "Existing ${System.currentTimeMillis()}"
        val id = runBlocking { container.noteRepository.create(title = title, description = "body") }
        val intent = QuickNoteActivity.editNoteIntent(context, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<QuickNoteActivity>(intent).use {
            // The note loads asynchronously, and the description seeds a frame behind the directly-bound
            // title, so it is the last field to appear — wait for it before asserting the prefill. The
            // description's contract is that it holds the stored body (a content check), not that it is
            // pixel-visible at that instant (which would race the sheet's slide-in, as it sits low).
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("body").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(title).assertIsDisplayed()
            composeRule.onNodeWithText("body").assertExists()
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
