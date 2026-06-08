package dev.davidfdev.stela.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.davidfdev.stela.ui.notelist.NoteListScreen
import dev.davidfdev.stela.ui.notelist.NoteListUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteListBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(notificationsBlocked: Boolean) {
        composeRule.setContent {
            StelaTheme(darkTheme = true) {
                NoteListScreen(
                    state = NoteListUiState(),
                    onAddNote = {},
                    onOpenNote = {},
                    onOpenSettings = {},
                    onTogglePin = {},
                    snackbarHostState = SnackbarHostState(),
                    notificationsBlocked = notificationsBlocked,
                    onOpenNotificationSettings = {},
                )
            }
        }
    }

    @Test
    fun banner_shown_whenNotificationsBlocked() {
        setContent(notificationsBlocked = true)
        composeRule.onNodeWithText("Open settings").assertIsDisplayed()
    }

    @Test
    fun banner_hidden_whenNotBlocked() {
        setContent(notificationsBlocked = false)
        composeRule.onAllNodesWithText("Open settings").assertCountEquals(0)
    }
}
