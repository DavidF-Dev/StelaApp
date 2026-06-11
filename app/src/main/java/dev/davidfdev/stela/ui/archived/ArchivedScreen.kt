package dev.davidfdev.stela.ui.archived

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.davidfdev.stela.R
import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.data.displayTitle
import dev.davidfdev.stela.ui.TimeFormatter

@Composable
fun ArchivedRoute(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    viewModel: ArchivedViewModel = viewModel(factory = ArchivedViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ArchivedEvent.NotesDeleted -> {
                    val message = context.resources.getQuantityString(
                        R.plurals.notelist_deleted, event.count, event.count,
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                }
            }
        }
    }

    ArchivedScreen(
        state = state,
        onBack = onBack,
        onOpenNote = onOpenNote,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onRestore = viewModel::restoreSelected,
        onDelete = viewModel::deleteSelected,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    state: ArchivedUiState,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = state.inSelectionMode) { onClearSelection() }

    Scaffold(
        topBar = {
            if (state.inSelectionMode) {
                ArchivedSelectionTopBar(
                    count = state.selectedCount,
                    allSelected = state.allSelected,
                    onClose = onClearSelection,
                    onToggleSelectAll = onToggleSelectAll,
                    onRestore = onRestore,
                    onDelete = { showDeleteConfirm = true },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.archived_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.notes.isEmpty()) {
                EmptyState(stringResource(R.string.archived_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.notes, key = { it.id }) { note ->
                        ArchivedRow(
                            note = note,
                            selected = note.id in state.selectedIds,
                            selectionMode = state.inSelectionMode,
                            onClick = {
                                if (state.inSelectionMode) onToggleSelection(note.id) else onOpenNote(note.id)
                            },
                            onLongClick = { onToggleSelection(note.id) },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            count = state.selectedCount,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivedSelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onRestore: () -> Unit,
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
            IconButton(onClick = onToggleSelectAll) {
                if (allSelected) {
                    Icon(Icons.Filled.Deselect, contentDescription = stringResource(R.string.action_deselect_all))
                } else {
                    Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                }
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Filled.Unarchive, contentDescription = stringResource(R.string.action_restore))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchivedRow(
    note: Note,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val relativeTime = remember(note.updatedAt) { TimeFormatter.relative(note.updatedAt).toString() }
    ListItem(
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        overlineContent = { Text(relativeTime) },
        headlineContent = { Text(note.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (note.description.isNotBlank()) {
                Text(note.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun DeleteConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.notelist_delete_dialog_title, count, count)) },
        text = { Text(pluralStringResource(R.plurals.notelist_delete_dialog_message, count, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
