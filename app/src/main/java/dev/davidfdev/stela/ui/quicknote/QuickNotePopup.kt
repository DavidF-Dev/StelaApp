package dev.davidfdev.stela.ui.quicknote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.davidfdev.stela.R
import dev.davidfdev.stela.ui.editor.EditorViewModel
import dev.davidfdev.stela.ui.editor.NoteDraft
import dev.davidfdev.stela.ui.editor.NoteFields
import kotlinx.coroutines.launch

/// The quick-note bottom-sheet popup: the shared [NoteFields] plus Save and Expand, hosted in a
/// transparent activity so it floats over whatever is on screen. New notes pin on save (no pin
/// toggle — saving is the contract); Expand carries the unsaved edit into the full editor.
/// [noteId] is null for a new note, set when editing an existing one.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickNotePopup(
    viewModel: EditorViewModel,
    noteId: Long?,
    onExpand: (NoteDraft) -> Unit,
    onFinished: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // After the save's DB work, slide the sheet down then finish the transparent activity; a bare
    // finish would snap the window away with no exit animation.
    val hideThenFinish: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onFinished() }
    }

    ModalBottomSheet(
        onDismissRequest = onFinished,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .imePadding(),
        ) {
            // Heading on the left; Expand (icon-only) and Save share the top-right, like an app bar.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (state.isEditing) R.string.quick_note_title_edit else R.string.quick_note_title_new,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    onExpand(NoteDraft(noteId, state.title, state.description, state.emoji, state.isPinned))
                }) {
                    Icon(Icons.Filled.OpenInFull, contentDescription = stringResource(R.string.quick_note_expand_description))
                }
                TextButton(onClick = { viewModel.save(hideThenFinish) }, enabled = state.canSave) {
                    Text(stringResource(R.string.editor_save))
                }
            }
            NoteFields(
                title = state.title,
                description = state.description,
                emoji = state.emoji,
                noteLoaded = state.noteLoaded,
                onTitleChange = viewModel::onTitleChange,
                onDescriptionChange = viewModel::onDescriptionChange,
                onEmojiChange = viewModel::onEmojiChange,
                modifier = Modifier.padding(top = 8.dp),
                descriptionModifier = Modifier.heightIn(min = 96.dp),
            )
        }
    }
}
