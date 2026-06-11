package dev.davidfdev.stela.ui.editor

import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.vanniktech.emoji.EmojiTheming
import com.vanniktech.emoji.EmojiView
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
private const val POP_START_DELAY_MILLIS = 300L

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
        onToggleArchive = { if (state.isArchived) viewModel.unarchive() else viewModel.archive() },
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
    onToggleArchive: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    // A brief scale "pop" draws the eye to the pin when an unpinned note opens (pinning is the app's
    // purpose). Keyed on noteLoaded so it runs once per open — for an existing note that is when the
    // note has loaded (createdAt becomes non-null). Skipped when the system animation scale is off.
    val context = LocalContext.current
    val pinPop = remember { Animatable(1f) }
    val noteLoaded = !state.isEditing || state.createdAt != null
    LaunchedEffect(noteLoaded) {
        val animationsOn = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) != 0f
        if (noteLoaded && !state.isPinned && animationsOn) {
            // Let the editor settle on screen before the pop, so it reads as a deliberate nudge.
            delay(POP_START_DELAY_MILLIS)
            pinPop.snapTo(1f)
            pinPop.animateTo(1.35f, tween(durationMillis = 140))
            pinPop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    // Route system back through the same exit as the back arrow so a cold notification launch finishes the task.
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                // Heading only for a new note, which has room (just Pin + Save); an existing note's
                // full action row (Share/Pin/Archive/Delete/Save) needs the width, so it shows none.
                title = {
                    if (!state.isEditing) Text(stringResource(R.string.editor_title_new))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.isEditing) {
                        // Share is hidden on a brand-new note; greyed when an existing note has no content.
                        IconButton(
                            onClick = onShare,
                            enabled = state.title.isNotBlank() || state.description.isNotBlank(),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.graphicsLayer { scaleX = pinPop.value; scaleY = pinPop.value },
                    ) {
                        if (state.isPinned) {
                            Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.action_unpin))
                        } else {
                            Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.action_pin))
                        }
                    }
                    if (state.isEditing) {
                        IconButton(onClick = onToggleArchive) {
                            if (state.isArchived) {
                                Icon(Icons.Filled.Unarchive, contentDescription = stringResource(R.string.action_restore))
                            } else {
                                Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.action_archive))
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
                .fillMaxSize(),
        ) {
            if (state.isArchived) ArchivedBanner()
            Column(
                modifier = Modifier
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
        EmojiPickerBottomSheet(
            showClear = state.emoji.isNotBlank(),
            onPick = { emoji ->
                onEmojiChange(emoji)
                showEmojiPicker = false
            },
            onClear = {
                onEmojiChange("")
                showEmojiPicker = false
            },
            onDismiss = { showEmojiPicker = false },
        )
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

/// Hosts a vanniktech `EmojiView` (categories + search) in a Material `BottomSheetDialog`. A
/// Compose `ModalBottomSheet` steals the vertical drag from the picker's `RecyclerView` (so it
/// cannot scroll); the View-system bottom sheet coordinates that scroll natively. The picker's
/// colours are passed explicitly from the Compose colour scheme (its own `EmojiTheming.from`
/// defaults to fixed light colours that ignore dark mode); the host theme wrapper drives the
/// "Clear" button and the search dialog's Material 3 chrome.
@Composable
private fun EmojiPickerBottomSheet(
    showClear: Boolean,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val darkTheme = colorScheme.surface.luminance() < 0.5f
    val theming = EmojiTheming(
        backgroundColor = colorScheme.surface.toArgb(),
        primaryColor = colorScheme.onSurfaceVariant.toArgb(),
        secondaryColor = colorScheme.primary.toArgb(),
        dividerColor = colorScheme.outlineVariant.toArgb(),
        textColor = colorScheme.onSurface.toArgb(),
        textSecondaryColor = colorScheme.onSurfaceVariant.toArgb(),
    )
    val onPickCurrent by rememberUpdatedState(onPick)
    val onClearCurrent by rememberUpdatedState(onClear)
    val onDismissCurrent by rememberUpdatedState(onDismiss)

    DisposableEffect(theming, showClear) {
        val themed = ContextThemeWrapper(
            context,
            if (darkTheme) {
                com.google.android.material.R.style.Theme_Material3_Dark
            } else {
                com.google.android.material.R.style.Theme_Material3_Light
            },
        )
        val content = LayoutInflater.from(themed).inflate(R.layout.emoji_picker_sheet, null)
        content.findViewById<MaterialButton>(R.id.clear_emoji).apply {
            visibility = if (showClear) View.VISIBLE else View.GONE
            setOnClickListener { onClearCurrent() }
        }
        val emojiView = content.findViewById<EmojiView>(R.id.emoji_picker)
        // editText = null: a single emoji is collected via the click listener, not typed into a field.
        emojiView.setUp(
            rootView = content,
            onEmojiClickListener = { onPickCurrent(it.unicode) },
            onEmojiBackspaceClickListener = null,
            editText = null,
            theming = theming,
        )

        val dialog = BottomSheetDialog(themed).apply {
            setContentView(content)
            setOnDismissListener { onDismissCurrent() }
            // The search box lives in a separate dialog, so keep its keyboard from shifting this grid.
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            // Disable the sheet's drag (scrim/back still dismiss) so the picker's RecyclerView gets the scroll.
            behavior.isDraggable = false
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            show()
        }
        onDispose {
            emojiView.tearDown()
            dialog.dismiss()
        }
    }
}
