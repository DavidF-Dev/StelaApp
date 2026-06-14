package dev.davidfdev.stela.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.ui.notelist.NoteListScreen
import dev.davidfdev.stela.ui.notelist.NoteListUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduledIndicatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val future = now + 3 * 60 * 60 * 1000L

    private fun note(id: Long, title: String, isPinned: Boolean = false, pinAt: Long? = null, unpinAt: Long? = null) =
        Note(id = id, title = title, description = "", createdAt = now, updatedAt = now,
            isPinned = isPinned, pinAt = pinAt, unpinAt = unpinAt)

    private fun setContent(vararg notes: Note) {
        composeRule.setContent {
            StelaTheme(darkTheme = true) {
                NoteListScreen(
                    state = NoteListUiState(notes = notes.toList(), isSourceEmpty = notes.isEmpty()),
                    onAddNote = {}, onOpenNote = {}, onOpenSettings = {}, onOpenArchived = {},
                    onTogglePin = {}, onToggleSelection = {}, onClearSelection = {}, onToggleSelectAll = {},
                    onBatchTogglePin = {}, onBatchArchive = {}, onBatchDelete = {},
                    onSearchChange = {}, onSortChange = {}, onToggleSortDirection = {}, onFilterChange = {},
                    snackbarHostState = SnackbarHostState(),
                    notificationsBlocked = false,
                    onOpenNotificationSettings = {},
                )
            }
        }
    }

    @Test
    fun unpinnedNoteWithPinAt_showsPinsLabel() {
        setContent(note(id = 1, title = "Scheduled", pinAt = future))
        composeRule.onNodeWithText("Pins", substring = true).assertIsDisplayed()
    }

    @Test
    fun pinnedNoteWithUnpinAt_showsUnpinsLabel() {
        setContent(note(id = 1, title = "Temporary", isPinned = true, unpinAt = future))
        composeRule.onNodeWithText("Unpins", substring = true).assertIsDisplayed()
    }

    @Test
    fun unscheduledNote_showsNoScheduleLabel() {
        setContent(note(id = 1, title = "Plain"))
        composeRule.onAllNodesWithText("Pins", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Unpins", substring = true).assertCountEquals(0)
    }
}
