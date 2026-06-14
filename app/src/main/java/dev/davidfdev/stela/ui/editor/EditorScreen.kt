package dev.davidfdev.stela.ui.editor

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.davidfdev.stela.R
import dev.davidfdev.stela.data.displayTitle
import dev.davidfdev.stela.ui.TimeFormatter
import dev.davidfdev.stela.ui.openAppNotificationSettings
import dev.davidfdev.stela.ui.rememberNotificationPermissionGate
import dev.davidfdev.stela.ui.shareNote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// How long the editor settles before the unpinned-pin "pop" plays, so it reads as a deliberate nudge.
private const val POP_START_DELAY_MILLIS = 600L

// Anchors the (currently empty) Advanced body so a test can assert it expands and collapses.
const val ADVANCED_CONTENT_TEST_TAG = "advancedContent"

@Composable
fun EditorRoute(
    onDone: () -> Unit,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val blockedMessage = stringResource(R.string.snackbar_pinning_needs_notifications)
    val settingsAction = stringResource(R.string.action_settings)
    val gate = rememberNotificationPermissionGate(
        onDenied = {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = blockedMessage,
                    actionLabel = settingsAction,
                )
                if (result == SnackbarResult.ActionPerformed) openAppNotificationSettings(context)
            }
        },
    )
    val duplicatedMessage = stringResource(R.string.editor_duplicated)
    val undoLabel = stringResource(R.string.action_undo)

    EditorScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onTitleChange = viewModel::onTitleChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onEmojiChange = viewModel::onEmojiChange,
        onPinAtChange = viewModel::onPinAtChange,
        onUnpinAtChange = viewModel::onUnpinAtChange,
        onTogglePin = { if (state.isPinned) viewModel.unpin() else gate { viewModel.pin() } },
        onToggleArchive = { if (state.isArchived) viewModel.unarchive() else viewModel.archive() },
        onShare = { shareNote(context, displayTitle(state.emoji, state.title), state.description) },
        onSave = { viewModel.save(onDone) },
        onDelete = { viewModel.delete(onDone) },
        onSnooze = viewModel::snooze,
        onDuplicate = {
            viewModel.duplicate {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(duplicatedMessage, undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDuplicate()
                }
            }
        },
        onToggleAdvanced = viewModel::setAdvancedExpanded,
        onBack = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    snackbarHostState: SnackbarHostState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onEmojiChange: (String) -> Unit,
    onPinAtChange: (Long?) -> Unit,
    onUnpinAtChange: (Long?) -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onSnooze: (Long) -> Unit,
    onDuplicate: () -> Unit,
    onToggleAdvanced: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // A brief scale "pop" draws the eye to the pin when an unpinned note opens (pinning is the app's
    // purpose). Keyed on noteLoaded so it runs once per open — for an existing note that is when the
    // note has loaded (createdAt becomes non-null). Skipped when the system animation scale is off.
    val context = LocalContext.current
    val pinPop = remember { Animatable(1f) }
    LaunchedEffect(state.noteLoaded) {
        val animationsOn = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) != 0f
        if (state.noteLoaded && !state.isPinned && animationsOn) {
            // Let the editor settle on screen before the pop, so it reads as a deliberate nudge.
            delay(POP_START_DELAY_MILLIS)
            pinPop.snapTo(1f)
            pinPop.animateTo(1.35f, tween(durationMillis = 140))
            pinPop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    // Dismiss the keyboard before leaving so it animates away in sync, rather than lingering over the
    // next screen while the system catches up; called on every exit path (back, save, delete).
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    // Leaving with unsaved edits confirms first; a clean note exits straight away. Used by both the back
    // arrow and system back so a cold notification launch still finishes the task once confirmed.
    val attemptBack = {
        dismissKeyboard()
        if (state.isDirty) showDiscardDialog = true else onBack()
    }

    BackHandler { attemptBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                // Heading only for a new note, which has room (just Pin + Save); an existing note's
                // full action row (Share/Pin/Archive/Delete/Save) needs the width, so it shows none.
                title = {
                    if (!state.isEditing) Text(stringResource(R.string.editor_title_new))
                },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // Share is hidden on a brand-new note; greyed when an existing note has no content.
                    NoteEditorActions(
                        state = state,
                        onShare = onShare,
                        onTogglePin = onTogglePin,
                        onArchive = onToggleArchive,
                        onDelete = { showDeleteDialog = true },
                        onSave = { dismissKeyboard(); onSave() },
                        onSnooze = onSnooze,
                        onDuplicate = onDuplicate,
                        pinModifier = Modifier.graphicsLayer { scaleX = pinPop.value; scaleY = pinPop.value },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                // Edge-to-edge means the framework won't inset for the keyboard; lift the body above it so
                // the description's caret stays visible while the app bar (a separate slot) stays pinned.
                .imePadding(),
        ) {
            if (state.isArchived) ArchivedBanner()
            // The page scrolls so the timestamps, Advanced section, and bottom spacer stay reachable on
            // short phones; the description is a fixed compact height (set in NoteFields) and scrolls within.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                NoteFields(
                    title = state.title,
                    description = state.description,
                    emoji = state.emoji,
                    noteLoaded = state.noteLoaded,
                    onTitleChange = onTitleChange,
                    onDescriptionChange = onDescriptionChange,
                    onEmojiChange = onEmojiChange,
                )

                val created = state.createdAt
                val updated = state.updatedAt
                if (created != null && updated != null) {
                    Text(
                        text = stringResource(
                            R.string.editor_timestamps,
                            TimeFormatter.absolute(created),
                            TimeFormatter.absolute(updated),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                AdvancedSection(
                    expanded = state.advancedExpanded,
                    onToggle = onToggleAdvanced,
                ) {
                    ScheduleControls(
                        pinAt = state.pinAt,
                        unpinAt = state.unpinAt,
                        isPinned = state.isPinned,
                        onPinAtChange = onPinAtChange,
                        onUnpinAtChange = onUnpinAtChange,
                    )
                }

                // Breathing room so the last control can scroll clear of the bottom, as Settings does.
                Spacer(Modifier.height(48.dp))
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
                    dismissKeyboard()
                    onDelete()
                }) { Text(stringResource(R.string.editor_delete_dialog_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.editor_delete_dialog_cancel)) }
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.editor_discard_dialog_title)) },
            text = { Text(stringResource(R.string.editor_discard_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text(stringResource(R.string.editor_discard_dialog_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.editor_discard_dialog_cancel))
                }
            },
        )
    }
}

/// A collapsible "Advanced" area for editor-only note features, collapsed by default. Holds [content]
/// (the scheduling controls). Editor-only by virtue of living here rather than in the popup-shared
/// `NoteFields`.
@Composable
private fun AdvancedSection(
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "advancedChevron")
    val expandedLabel = stringResource(R.string.state_expanded)
    val collapsedLabel = stringResource(R.string.state_collapsed)

    Column(modifier = Modifier.padding(top = 12.dp)) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = expanded,
                    role = Role.Button,
                    onValueChange = onToggle,
                )
                .semantics { stateDescription = if (expanded) expandedLabel else collapsedLabel }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.editor_advanced),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Box(modifier = Modifier.testTag(ADVANCED_CONTENT_TEST_TAG)) { content() }
        }
    }
}

/// A full-width strip below the app bar marking the note as archived (it is otherwise edited
/// like any other). Restore lives in the bottom action bar.
@Composable
private fun ArchivedBanner() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.editor_archived_banner),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
