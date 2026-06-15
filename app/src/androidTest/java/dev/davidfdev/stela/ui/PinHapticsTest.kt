package dev.davidfdev.stela.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.ui.editor.EditorScreen
import dev.davidfdev.stela.ui.editor.EditorUiState
import dev.davidfdev.stela.ui.notelist.NoteListScreen
import dev.davidfdev.stela.ui.notelist.NoteListUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinHapticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingHapticFeedback : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    private fun note(isPinned: Boolean) =
        Note(id = 1, title = "Note", description = "", createdAt = 0L, updatedAt = 0L, isPinned = isPinned)

    private fun listContent(haptic: HapticFeedback, isPinned: Boolean) {
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                StelaTheme(darkTheme = true) {
                    NoteListScreen(
                        state = NoteListUiState(notes = listOf(note(isPinned)), isSourceEmpty = false),
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
    }

    @Test
    fun listRow_pinning_ticksToggleOn() {
        val haptic = RecordingHapticFeedback()
        listContent(haptic, isPinned = false)

        composeRule.onNodeWithContentDescription("Pin").performClick()

        assertEquals(listOf(HapticFeedbackType.ToggleOn), haptic.performed)
    }

    @Test
    fun listRow_unpinning_ticksToggleOff() {
        val haptic = RecordingHapticFeedback()
        listContent(haptic, isPinned = true)

        composeRule.onNodeWithContentDescription("Unpin").performClick()

        assertEquals(listOf(HapticFeedbackType.ToggleOff), haptic.performed)
    }

    @Test
    fun editor_pinning_ticksToggleOn() {
        val haptic = RecordingHapticFeedback()
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                StelaTheme(darkTheme = true) {
                    EditorScreen(
                        state = EditorUiState(),
                        snackbarHostState = SnackbarHostState(),
                        onTitleChange = {},
                        onDescriptionChange = {},
                        onEmojiChange = {},
                        onPinAtChange = {},
                        onUnpinAtChange = {},
                        onAlertOnPinChange = {},
                        onTogglePin = {},
                        onToggleArchive = {},
                        onShare = {},
                        onSave = {},
                        onDelete = {},
                        onSnooze = {},
                        onDuplicate = {},
                        onToggleAdvanced = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Pin").performClick()

        assertEquals(listOf(HapticFeedbackType.ToggleOn), haptic.performed)
    }
}
