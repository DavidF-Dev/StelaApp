package dev.davidfdev.stela.ui.quicknote

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

// Matched to the system keyboard's ~250 ms hide so the card's exit slide and the keyboard retract land
// together; the card is drawn in the host activity's own window, so the keyboard can be hidden in step
// (a separate sheet window cannot do this).
private const val POPUP_ANIM_MILLIS = 250

/// The quick-note popup: a bottom-anchored editor card drawn directly in the transparent host
/// activity's own window (not a separate sheet window) so the keyboard it raises can be hidden and
/// animated away in sync on close, as the full editor does. It mirrors the editor's action row
/// (Expand · Share · Pin/Unpin · Archive · Delete · Save) over the shared [NoteFields] — new notes pin
/// on save; Archive/Delete confirm before acting. [noteId] is null for a new note, set when editing one.
@Composable
internal fun QuickNotePopup(
    viewModel: EditorViewModel,
    noteId: Long?,
    onExpand: (NoteDraft) -> Unit,
    onFinished: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    // Plays the enter slide on first composition; a dismiss flips the target to false to play the exit
    // slide, and the keyboard is hidden at the same instant so the two animate away together.
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visibleState.targetState = true }
    var closing by remember { mutableStateOf(false) }

    val dismiss: () -> Unit = dismiss@{
        if (closing) return@dismiss
        closing = true
        dismissKeyboard()
        visibleState.targetState = false
    }
    // Finish only once the exit slide has fully played out (the keyboard has retracted alongside it).
    LaunchedEffect(visibleState.currentState, visibleState.isIdle) {
        if (closing && visibleState.isIdle && !visibleState.currentState) onFinished()
    }

    // System back dismisses with the same animated exit as the back arrow and scrim tap.
    BackHandler { dismiss() }

    val spec = tween<Float>(POPUP_ANIM_MILLIS)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visibleState = visibleState, enter = fadeIn(spec), exit = fadeOut(spec)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visibleState = visibleState,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(POPUP_ANIM_MILLIS)) { it },
            exit = slideOutVertically(tween(POPUP_ANIM_MILLIS)) { it },
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                // Swallow taps on the card so they don't fall through to the scrim and dismiss it.
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Lift the content above the keyboard, or the navigation bar when it is down.
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                        // Top padding stands in for the now-removed drag handle's spacing.
                        .padding(top = 8.dp, bottom = 16.dp),
                ) {
                    // Back arrow + heading (new notes only, mirroring the editor) on the left; the shared
                    // action cluster on the right. Hug the edges (app-bar inset) so Back and Save sit near
                    // the screen edges, leaving the row more room than the 16 dp the fields use.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = dismiss) {
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
                            onSave = { viewModel.save(dismiss) },
                            onSnooze = viewModel::snooze,
                            onExpand = {
                                dismissKeyboard()
                                onExpand(NoteDraft(noteId, state.title, state.description, state.emoji, state.isPinned))
                            },
                        )
                    }
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        NoteFields(
                            title = state.title,
                            description = state.description,
                            emoji = state.emoji,
                            noteLoaded = state.noteLoaded,
                            onTitleChange = viewModel::onTitleChange,
                            onDescriptionChange = viewModel::onDescriptionChange,
                            onEmojiChange = viewModel::onEmojiChange,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    SnackbarHost(snackbarHostState)
                }
            }
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
                    viewModel.delete(dismiss)
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
                    viewModel.archive(dismiss)
                }) { Text(stringResource(R.string.editor_archive_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text(stringResource(R.string.editor_delete_dialog_cancel)) }
            },
        )
    }
}
