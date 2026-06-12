package dev.davidfdev.stela.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.davidfdev.stela.ui.editor.ADVANCED_CONTENT_TEST_TAG
import dev.davidfdev.stela.ui.editor.EditorScreen
import dev.davidfdev.stela.ui.editor.EditorUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvancedSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent() {
        composeRule.setContent {
            StelaTheme(darkTheme = true) {
                EditorScreen(
                    state = EditorUiState(),
                    snackbarHostState = SnackbarHostState(),
                    onTitleChange = {},
                    onDescriptionChange = {},
                    onEmojiChange = {},
                    onPinAtChange = {},
                    onUnpinAtChange = {},
                    onTogglePin = {},
                    onToggleArchive = {},
                    onShare = {},
                    onSave = {},
                    onDelete = {},
                    onSnooze = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun advanced_isCollapsedByDefault() {
        setContent()
        composeRule.onNodeWithText("Advanced").assertIsDisplayed()
        // Collapsed: the body isn't composed (AnimatedVisibility hidden).
        composeRule.onNodeWithTag(ADVANCED_CONTENT_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun advanced_expandsAndCollapsesOnTap() {
        setContent()
        composeRule.onNodeWithText("Advanced").performClick()
        composeRule.onNodeWithTag(ADVANCED_CONTENT_TEST_TAG).assertExists()

        composeRule.onNodeWithText("Advanced").performClick()
        composeRule.onNodeWithTag(ADVANCED_CONTENT_TEST_TAG).assertDoesNotExist()
    }
}
