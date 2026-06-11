package dev.davidfdev.stela.ui.quicknote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.davidfdev.stela.R
import dev.davidfdev.stela.data.displayTitle
import dev.davidfdev.stela.ui.editor.EditorViewModel
import dev.davidfdev.stela.ui.editor.NoteDraft
import dev.davidfdev.stela.ui.editor.NoteEditorActions
import dev.davidfdev.stela.ui.editor.NoteFields
import dev.davidfdev.stela.ui.openAppNotificationSettings
import dev.davidfdev.stela.ui.rememberNotificationPermissionGate
import dev.davidfdev.stela.ui.shareNote
import kotlinx.coroutines.launch

/// The quick-note bottom-sheet popup, hosted in a transparent activity so it floats over whatever is on
/// screen. It mirrors the full editor's action row (Expand · Share · Pin/Unpin · Archive · Delete ·
/// Save) over the shared [NoteFields] — new notes pin on save; Archive/Delete confirm before acting.
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val blockedMessage = stringResource(R.string.snackbar_pinning_needs_notifications)
    val settingsAction = stringResource(R.string.action_settings)
    val gate = rememberNotificationPermissionGate(
        onDenied = {
            scope.launch {
                val result = snackbarHostState.showSnackbar(blockedMessage, actionLabel = settingsAction)
                if (result == SnackbarResult.ActionPerformed) openAppNotificationSettings(context)
            }
        },
    )

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }

    // After the note-changing work, slide the sheet down then finish the transparent activity; a bare
    // finish would snap the window away with no exit animation.
    val hideThenFinish: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onFinished() }
    }

    ModalBottomSheet(
        onDismissRequest = onFinished,
        sheetState = sheetState,
        // No drag handle, and dragging the sheet is disabled, so dragging to scroll a long description
        // can't drag the whole popup away (scrim tap, system back, and the back button still dismiss).
        sheetGesturesEnabled = false,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                // Top padding stands in for the now-removed drag handle's spacing.
                .padding(top = 8.dp, bottom = 16.dp)
                .imePadding(),
        ) {
            // Back arrow + heading (new notes only, mirroring the editor) on the left; the shared action
            // cluster on the right, led by Expand.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = hideThenFinish) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                if (!state.isEditing) {
                    Text(
                        text = stringResource(R.string.quick_note_title_new),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                NoteEditorActions(
                    state = state,
                    onShare = { shareNote(context, displayTitle(state.emoji, state.title), state.description) },
                    onTogglePin = { if (state.isPinned) viewModel.unpin() else gate { viewModel.pin() } },
                    onArchive = { showArchiveDialog = true },
                    onDelete = { showDeleteDialog = true },
                    onSave = { viewModel.save(hideThenFinish) },
                    onExpand = {
                        onExpand(NoteDraft(noteId, state.title, state.description, state.emoji, state.isPinned))
                    },
                )
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                NoteFields(
                    title = state.title,
                    description = state.description,
                    emoji = state.emoji,
                    noteLoaded = state.noteLoaded,
                    onTitleChange = viewModel::onTitleChange,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    onEmojiChange = viewModel::onEmojiChange,
                    modifier = Modifier.padding(top = 8.dp),
                    // Grows with content up to double the default height, then scrolls within.
                    descriptionModifier = Modifier.heightIn(min = 96.dp, max = 192.dp),
                )
            }
            SnackbarHost(snackbarHostState)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.editor_delete_dialog_title)) },
            text = { Text(stringResource(R.string.editor_delete_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(hideThenFinish)
                }) { Text(stringResource(R.string.editor_delete_dialog_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.editor_delete_dialog_cancel)) }
            },
        )
    }

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text(stringResource(R.string.editor_archive_dialog_title)) },
            text = { Text(stringResource(R.string.editor_archive_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveDialog = false
                    viewModel.archive(hideThenFinish)
                }) { Text(stringResource(R.string.editor_archive_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text(stringResource(R.string.editor_delete_dialog_cancel)) }
            },
        )
    }
}
