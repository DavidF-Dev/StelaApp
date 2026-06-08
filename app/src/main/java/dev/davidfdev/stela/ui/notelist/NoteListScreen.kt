package dev.davidfdev.stela.ui.notelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.davidfdev.stela.data.Note
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
    val gate = rememberNotificationPermissionGate(
        onDenied = {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Notifications are off — pinning needs them.",
                    actionLabel = "Settings",
                )
                if (result == SnackbarResult.ActionPerformed) openAppNotificationSettings(context)
            }
        },
    )
    val onTogglePin: (Note) -> Unit = { note ->
        if (note.isPinned) viewModel.unpin(note) else gate { viewModel.pin(note) }
    }

    NoteListScreen(
        state = state,
        onAddNote = onAddNote,
        onOpenNote = onOpenNote,
        onOpenSettings = onOpenSettings,
        onTogglePin = onTogglePin,
        snackbarHostState = snackbarHostState,
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
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stela") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNote) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        if (state.notes.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onClick = { onOpenNote(note.id) },
                        onTogglePin = { onTogglePin(note) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, onClick: () -> Unit, onTogglePin: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(note.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            if (note.description.isNotBlank()) {
                Text(note.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            IconButton(onClick = onTogglePin) {
                if (note.isPinned) {
                    Icon(Icons.Filled.PushPin, contentDescription = "Unpin")
                } else {
                    Icon(Icons.Outlined.PushPin, contentDescription = "Pin")
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No notes yet.\nTap + to create one.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
