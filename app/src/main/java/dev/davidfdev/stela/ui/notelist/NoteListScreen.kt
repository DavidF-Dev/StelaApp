package dev.davidfdev.stela.ui.notelist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.davidfdev.stela.R
import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.data.displayTitle
import dev.davidfdev.stela.ui.TimeFormatter
import dev.davidfdev.stela.ui.arePinnedNotificationsBlocked
import dev.davidfdev.stela.ui.openAppNotificationSettings
import dev.davidfdev.stela.ui.rememberNotificationPermissionGate
import kotlinx.coroutines.launch

@Composable
fun NoteListRoute(
    onAddNote: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: NoteListViewModel = viewModel(factory = NoteListViewModel.Factory),
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
    val onTogglePin: (Note) -> Unit = { note ->
        if (note.isPinned) viewModel.unpin(note) else gate { viewModel.pin(note) }
    }
    // The smart toggle pins only when the selection has an unpinned note, so gate just that case.
    val onBatchTogglePin: () -> Unit = {
        if (state.batchActionPins) gate { viewModel.batchTogglePin() } else viewModel.batchTogglePin()
    }

    var notificationsBlocked by remember { mutableStateOf(arePinnedNotificationsBlocked(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsBlocked = arePinnedNotificationsBlocked(context)
    }

    NoteListScreen(
        state = state,
        onAddNote = onAddNote,
        onOpenNote = onOpenNote,
        onOpenSettings = onOpenSettings,
        onTogglePin = onTogglePin,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onBatchTogglePin = onBatchTogglePin,
        onBatchDelete = viewModel::batchDelete,
        snackbarHostState = snackbarHostState,
        notificationsBlocked = notificationsBlocked,
        onOpenNotificationSettings = { openAppNotificationSettings(context) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    state: NoteListUiState,
    onAddNote: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onTogglePin: (Note) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onBatchTogglePin: () -> Unit,
    onBatchDelete: () -> Unit,
    snackbarHostState: SnackbarHostState,
    notificationsBlocked: Boolean,
    onOpenNotificationSettings: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = state.inSelectionMode) { onClearSelection() }

    Scaffold(
        topBar = {
            if (state.inSelectionMode) {
                SelectionTopBar(
                    count = state.selectedCount,
                    pinAction = state.batchActionPins,
                    onClose = onClearSelection,
                    onTogglePin = onBatchTogglePin,
                    onDelete = { showDeleteConfirm = true },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.inSelectionMode) {
                FloatingActionButton(onClick = onAddNote) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_note))
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (notificationsBlocked) {
                NotificationsBlockedBanner(onOpenSettings = onOpenNotificationSettings)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.notes.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.notes, key = { it.id }) { note ->
                            NoteRow(
                                note = note,
                                selected = note.id in state.selectedIds,
                                selectionMode = state.inSelectionMode,
                                onClick = {
                                    if (state.inSelectionMode) onToggleSelection(note.id) else onOpenNote(note.id)
                                },
                                onLongClick = { onToggleSelection(note.id) },
                                onTogglePin = { onTogglePin(note) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val count = state.selectedCount
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(pluralStringResource(R.plurals.notelist_delete_dialog_title, count, count)) },
            text = { Text(pluralStringResource(R.plurals.notelist_delete_dialog_message, count, count)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onBatchDelete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    pinAction: Boolean,
    onClose: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close_selection))
            }
        },
        title = { Text(pluralStringResource(R.plurals.notelist_selected_count, count, count)) },
        actions = {
            IconButton(onClick = onTogglePin) {
                if (pinAction) {
                    Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.action_pin))
                } else {
                    Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.action_unpin))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        },
    )
}

@Composable
private fun NotificationsBlockedBanner(onOpenSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.notelist_notifications_blocked),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: Note,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    // DateUtils formatting is recomputed only when the timestamp changes, not every recomposition.
    val relativeTime = remember(note.updatedAt) { TimeFormatter.relative(note.updatedAt).toString() }
    ListItem(
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        overlineContent = {
            Text(relativeTime)
        },
        headlineContent = {
            Text(note.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            if (note.description.isNotBlank()) {
                Text(note.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            if (selectionMode) {
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                IconButton(onClick = onTogglePin) {
                    if (note.isPinned) {
                        Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.action_unpin))
                    } else {
                        Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.action_pin))
                    }
                }
            }
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.notelist_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
