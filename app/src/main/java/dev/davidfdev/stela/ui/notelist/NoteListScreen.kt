package dev.davidfdev.stela.ui.notelist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import dev.davidfdev.stela.settings.NoteFilter
import dev.davidfdev.stela.settings.SortOrder
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
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NoteListEvent.NotesDeleted -> {
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
        onToggleSelectAll = viewModel::toggleSelectAll,
        onBatchTogglePin = onBatchTogglePin,
        onBatchDelete = viewModel::batchDelete,
        onSearchChange = viewModel::onSearchChange,
        onSortChange = viewModel::onSortChange,
        onToggleSortDirection = viewModel::onToggleSortDirection,
        onFilterChange = viewModel::onFilterChange,
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
    onToggleSelectAll: () -> Unit,
    onBatchTogglePin: () -> Unit,
    onBatchDelete: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onToggleSortDirection: () -> Unit,
    onFilterChange: (NoteFilter) -> Unit,
    snackbarHostState: SnackbarHostState,
    notificationsBlocked: Boolean,
    onOpenNotificationSettings: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var showSortFilter by remember { mutableStateOf(false) }

    val closeSearch = {
        searchActive = false
        onSearchChange("")
    }

    BackHandler(enabled = state.inSelectionMode) { onClearSelection() }
    BackHandler(enabled = searchActive && !state.inSelectionMode) { closeSearch() }

    Scaffold(
        topBar = {
            if (state.inSelectionMode) {
                SelectionTopBar(
                    count = state.selectedCount,
                    allSelected = state.allSelected,
                    pinAction = state.batchActionPins,
                    onClose = onClearSelection,
                    onToggleSelectAll = onToggleSelectAll,
                    onTogglePin = onBatchTogglePin,
                    onDelete = { showDeleteConfirm = true },
                )
            } else {
                NoteListTopBar(
                    searchActive = searchActive,
                    searchQuery = state.searchQuery,
                    onActivateSearch = { searchActive = true },
                    onCloseSearch = closeSearch,
                    onSearchChange = onSearchChange,
                    onOpenSortFilter = { showSortFilter = true },
                    onOpenSettings = onOpenSettings,
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (notificationsBlocked) {
                NotificationsBlockedBanner(onOpenSettings = onOpenNotificationSettings)
            }
            // Surface a non-default filter so a shortened list reads as "filtered", not "empty".
            if (!state.inSelectionMode && state.noteFilter != NoteFilter.ALL) {
                ActiveFilterChip(
                    filter = state.noteFilter,
                    onClear = { onFilterChange(NoteFilter.ALL) },
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    // No notes exist at all, vs. notes exist but the query hides them all.
                    state.isSourceEmpty -> EmptyState(stringResource(R.string.notelist_empty))
                    state.notes.isEmpty() -> EmptyState(stringResource(R.string.notelist_no_matches))
                    else -> LazyColumn(
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

    if (showSortFilter) {
        SortFilterSheet(
            sortOrder = state.sortOrder,
            sortReversed = state.sortReversed,
            noteFilter = state.noteFilter,
            onSortChange = onSortChange,
            onToggleSortDirection = onToggleSortDirection,
            onFilterChange = onFilterChange,
            onDismiss = { showSortFilter = false },
        )
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
    allSelected: Boolean,
    pinAction: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
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
            IconButton(onClick = onToggleSelectAll) {
                if (allSelected) {
                    Icon(Icons.Filled.Deselect, contentDescription = stringResource(R.string.action_deselect_all))
                } else {
                    Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                }
            }
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
private fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteListTopBar(
    searchActive: Boolean,
    searchQuery: String,
    onActivateSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenSortFilter: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (searchActive) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onCloseSearch) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_search_close),
                    )
                }
            },
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(stringResource(R.string.notelist_search_hint)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_search_clear))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            },
        )
    } else {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                IconButton(onClick = onActivateSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                }
                IconButton(onClick = onOpenSortFilter) {
                    Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.action_sort_and_filter))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortFilterSheet(
    sortOrder: SortOrder,
    sortReversed: Boolean,
    noteFilter: NoteFilter,
    onSortChange: (SortOrder) -> Unit,
    onToggleSortDirection: () -> Unit,
    onFilterChange: (NoteFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            SheetSectionLabel(stringResource(R.string.notelist_sort_label))
            SortOrder.entries.forEach { order ->
                ChoiceRow(
                    label = stringResource(sortLabel(order)),
                    selected = order == sortOrder,
                    onClick = { onSortChange(order) },
                )
            }
            SortDirectionRow(
                label = stringResource(sortDirectionLabel(sortOrder, sortReversed)),
                onToggle = onToggleSortDirection,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SheetSectionLabel(stringResource(R.string.notelist_filter_label))
            NoteFilter.entries.forEach { filter ->
                ChoiceRow(
                    label = stringResource(filterLabel(filter)),
                    selected = filter == noteFilter,
                    onClick = { onFilterChange(filter) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveFilterChip(filter: NoteFilter, onClear: () -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        InputChip(
            selected = true,
            onClick = onClear,
            label = { Text(stringResource(filterLabel(filter))) },
            trailingIcon = {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.notelist_clear_filter),
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SortDirectionRow(label: String, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.action_sort_direction))
        }
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun sortLabel(order: SortOrder): Int = when (order) {
    SortOrder.MODIFIED -> R.string.notelist_sort_modified
    SortOrder.CREATED -> R.string.notelist_sort_created
    SortOrder.TITLE -> R.string.notelist_sort_title
}

private fun filterLabel(filter: NoteFilter): Int = when (filter) {
    NoteFilter.ALL -> R.string.notelist_filter_all
    NoteFilter.PINNED -> R.string.notelist_filter_pinned
    NoteFilter.UNPINNED -> R.string.notelist_filter_unpinned
}

// The direction label is order-aware: newest/oldest for the timestamps, A–Z/Z–A for title.
private fun sortDirectionLabel(order: SortOrder, reversed: Boolean): Int = when (order) {
    SortOrder.MODIFIED, SortOrder.CREATED ->
        if (reversed) R.string.notelist_sort_oldest_first else R.string.notelist_sort_newest_first
    SortOrder.TITLE ->
        if (reversed) R.string.notelist_sort_z_a else R.string.notelist_sort_a_z
}
