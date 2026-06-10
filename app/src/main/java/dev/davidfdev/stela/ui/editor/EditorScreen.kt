package dev.davidfdev.stela.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.davidfdev.stela.R
import dev.davidfdev.stela.data.displayTitle
import dev.davidfdev.stela.ui.TimeFormatter
import dev.davidfdev.stela.ui.openAppNotificationSettings
import dev.davidfdev.stela.ui.rememberNotificationPermissionGate
import dev.davidfdev.stela.ui.shareNote
import kotlinx.coroutines.launch

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

    EditorScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onTitleChange = viewModel::onTitleChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onEmojiChange = viewModel::onEmojiChange,
        onTogglePin = { if (state.isPinned) viewModel.unpin() else gate { viewModel.pin() } },
        onShare = { shareNote(context, displayTitle(state.emoji, state.title), state.description) },
        onSave = { viewModel.save(onDone) },
        onDelete = { viewModel.delete(onDone) },
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
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    // Route system back through the same exit as the back arrow so a cold notification launch finishes the task.
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEditing) R.string.editor_title_edit else R.string.editor_title_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.title.isNotBlank() || state.description.isNotBlank()) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                    if (state.isEditing) {
                        IconButton(onClick = onTogglePin) {
                            if (state.isPinned) {
                                Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.action_unpin))
                            } else {
                                Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.action_pin))
                            }
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    TextButton(onClick = onSave, enabled = state.canSave) {
                        Text(stringResource(R.string.editor_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.editor_label_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                leadingIcon = {
                    IconButton(onClick = { showEmojiPicker = true }) {
                        if (state.emoji.isBlank()) {
                            Icon(Icons.Outlined.Mood, contentDescription = stringResource(R.string.editor_add_emoji))
                        } else {
                            Text(state.emoji, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.editor_label_description)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(200.dp),
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
                    onDelete()
                }) { Text(stringResource(R.string.editor_delete_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.editor_delete_dialog_cancel)) }
            },
        )
    }

    if (showEmojiPicker) {
        ModalBottomSheet(onDismissRequest = { showEmojiPicker = false }) {
            if (state.emoji.isNotBlank()) {
                TextButton(
                    onClick = {
                        onEmojiChange("")
                        showEmojiPicker = false
                    },
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp),
                ) { Text(stringResource(R.string.editor_clear_emoji)) }
            }
            // The official picker is a View; host it and report the picked emoji.
            AndroidView(
                factory = { ctx ->
                    EmojiPickerView(ctx).apply {
                        setOnEmojiPickedListener { picked ->
                            onEmojiChange(picked.emoji)
                            showEmojiPicker = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            )
        }
    }
}
