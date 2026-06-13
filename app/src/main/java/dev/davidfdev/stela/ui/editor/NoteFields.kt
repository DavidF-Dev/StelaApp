package dev.davidfdev.stela.ui.editor

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.vanniktech.emoji.EmojiTheming
import com.vanniktech.emoji.EmojiView
import dev.davidfdev.stela.R
import dev.davidfdev.stela.ui.ButtonTooltip
import dev.davidfdev.stela.ui.TooltipIconButton

/// The shared note-editing core: an emoji-leading Title field and a Description field, plus the emoji
/// picker they open. Used by both the full editor and the quick-note popup so editing behaves
/// identically across surfaces. The Description is a fixed two-to-six lines and scrolls within once
/// full, so it stays compact and never dominates the surface.
@Composable
internal fun NoteFields(
    title: String,
    description: String,
    emoji: String,
    noteLoaded: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onEmojiChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEmojiPicker by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val titleFocusRequester = remember { FocusRequester() }

    // Focus the title and raise the keyboard once the note is loaded with a blank title — the new-note
    // case (and an expanded popup left empty). A prefilled title (existing note, or a carried-over
    // draft) keeps focus off so the keyboard does not pop.
    LaunchedEffect(noteLoaded) {
        if (noteLoaded && title.isBlank()) {
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.editor_label_title)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            leadingIcon = {
                IconButton(onClick = { showEmojiPicker = true }) {
                    if (emoji.isBlank()) {
                        Icon(Icons.Outlined.Mood, contentDescription = stringResource(R.string.editor_add_emoji))
                    } else {
                        Text(emoji, style = MaterialTheme.typography.titleLarge)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(titleFocusRequester),
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.editor_label_description)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            maxLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }

    if (showEmojiPicker) {
        EmojiPickerBottomSheet(
            showClear = emoji.isNotBlank(),
            onPick = { picked ->
                onEmojiChange(picked)
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

/// The shared editor action cluster — Pin/Unpin · Delete · ⋮ (Expand · Share · Archive/Restore) · Save —
/// used by both the full editor's app bar and the quick-note popup so the two stay in lockstep. Delete,
/// and the overflow's Share + Archive/Restore, show only for an existing note (`isEditing`); the surface
/// decides what each callback does (e.g. the popup confirms Archive/Delete and the editor toggles Archive
/// directly). The secondary actions live in an overflow menu to keep the row from crowding the
/// width-stable Save. [onExpand] is null for the full editor (no Expand there); [pinModifier] lets the
/// editor apply its pin "pop".
@Composable
internal fun RowScope.NoteEditorActions(
    state: EditorUiState,
    onShare: () -> Unit,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onSnooze: (Long) -> Unit,
    onExpand: (() -> Unit)? = null,
    pinModifier: Modifier = Modifier,
) {
    TooltipIconButton(
        icon = if (state.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
        label = stringResource(if (state.isPinned) R.string.action_unpin else R.string.action_pin),
        onClick = onTogglePin,
        modifier = pinModifier,
    )
    if (state.isEditing) {
        TooltipIconButton(Icons.Filled.Delete, stringResource(R.string.action_delete), onDelete)
    }
    // Shown only when it would hold at least one item (Expand for the popup, or Share/Archive for an
    // existing note).
    if (onExpand != null || state.isEditing) {
        NoteOverflowMenu(state = state, onExpand = onExpand, onShare = onShare, onArchive = onArchive, onSnooze = onSnooze)
    }
    // Filled so it reads as the primary action and stands out; an icon keeps its width locale-stable.
    ButtonTooltip(stringResource(R.string.editor_save)) {
        FilledIconButton(onClick = onSave, enabled = state.canSave) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.editor_save))
        }
    }
}

@Composable
private fun NoteOverflowMenu(
    state: EditorUiState,
    onExpand: (() -> Unit)?,
    onShare: () -> Unit,
    onArchive: () -> Unit,
    onSnooze: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showSnooze by remember { mutableStateOf(false) }
    // Box so the menu anchors to the icon button's bounds and drops down aligned to it.
    Box {
        TooltipIconButton(Icons.Filled.MoreVert, stringResource(R.string.action_more), onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            onExpand?.let { expand ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.quick_note_expand_description)) },
                    leadingIcon = { Icon(Icons.Filled.OpenInFull, contentDescription = null) },
                    onClick = { expanded = false; expand() },
                )
            }
            if (state.isEditing) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_share)) },
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    // Greyed when an existing note has no content to share.
                    enabled = state.title.isNotBlank() || state.description.isNotBlank(),
                    onClick = { expanded = false; onShare() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_snooze)) },
                    leadingIcon = { Icon(Icons.Filled.Snooze, contentDescription = null) },
                    // Snooze hides a note that's in the tray, so it only applies to a pinned note.
                    enabled = state.isPinned,
                    onClick = { expanded = false; showSnooze = true },
                )
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (state.isArchived) R.string.action_restore else R.string.action_archive))
                    },
                    leadingIcon = {
                        Icon(
                            if (state.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                            contentDescription = null,
                        )
                    },
                    onClick = { expanded = false; onArchive() },
                )
            }
        }
    }

    if (showSnooze) {
        SnoozeChooser(
            onPick = { until -> showSnooze = false; onSnooze(until) },
            onDismiss = { showSnooze = false },
        )
    }
}

/// Hosts a vanniktech `EmojiView` (categories + search) in a Material `BottomSheetDialog`. A
/// Compose `ModalBottomSheet` steals the vertical drag from the picker's `RecyclerView` (so it
/// cannot scroll); the View-system bottom sheet coordinates that scroll natively. The picker's
/// colours are passed explicitly from the Compose colour scheme (its own `EmojiTheming.from`
/// defaults to fixed light colours that ignore dark mode); the host theme wrapper drives the
/// "Clear" button and the search dialog's Material 3 chrome.
@Composable
internal fun EmojiPickerBottomSheet(
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
