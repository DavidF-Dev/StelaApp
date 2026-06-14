package dev.davidfdev.stela.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.davidfdev.stela.ui.editor.DESCRIPTION_FIELD_TEST_TAG
import dev.davidfdev.stela.ui.editor.EditorScreen
import dev.davidfdev.stela.ui.editor.EditorUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DescriptionFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(state: EditorUiState, onDescriptionChange: (String) -> Unit) {
        composeRule.setContent {
            StelaTheme(darkTheme = true) {
                EditorScreen(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onTitleChange = {},
                    onDescriptionChange = onDescriptionChange,
                    onEmojiChange = {},
                    onPinAtChange = {},
                    onUnpinAtChange = {},
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

    @Test
    fun description_seedsFromState() {
        setContent(EditorUiState(description = "Seeded body"), onDescriptionChange = {})
        composeRule.onNodeWithTag(DESCRIPTION_FIELD_TEST_TAG).assertTextContains("Seeded body")
    }

    @Test
    fun description_propagatesEditsBackToState() {
        var captured = ""
        // The field seeds with the cursor at the end, so typed text appends.
        setContent(EditorUiState(description = "Seed"), onDescriptionChange = { captured = it })
        composeRule.onNodeWithTag(DESCRIPTION_FIELD_TEST_TAG).performTextInput(" more")
        composeRule.runOnIdle { assertEquals("Seed more", captured) }
    }
}
