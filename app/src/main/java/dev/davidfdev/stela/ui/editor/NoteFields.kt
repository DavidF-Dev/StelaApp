package dev.davidfdev.stela.ui.editor

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/// The shared note-editing core: an emoji-leading Title field and a Description field, plus the emoji
/// picker they open. Used by both the full editor and the quick-note popup so editing behaves
/// identically across surfaces. [descriptionModifier] sizes the description field (the popup keeps it
/// compact; the editor gives it a fixed tall height).
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
    descriptionModifier: Modifier = Modifier,
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
            modifier = descriptionModifier
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

/// The shared editor action cluster — Expand · Share · Pin/Unpin · Archive · Delete · Save — used by
/// both the full editor's app bar and the quick-note popup so the two stay in lockstep. Share, Archive,
/// and Delete show only for an existing note (`isEditing`); the surface decides what each callback does
/// (e.g. the popup confirms Archive/Delete and the editor toggles Archive directly). [onExpand] is null
/// for the full editor (no Expand there); [pinModifier] lets the editor apply its pin "pop".
@Composable
internal fun RowScope.NoteEditorActions(
    state: EditorUiState,
    onShare: () -> Unit,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onExpand: (() -> Unit)? = null,
    pinModifier: Modifier = Modifier,
) {
    onExpand?.let { expand ->
        IconButton(onClick = expand) {
            Icon(Icons.Filled.OpenInFull, contentDescription = stringResource(R.string.quick_note_expand_description))
        }
    }
    if (state.isEditing) {
        // Greyed when an existing note has no content to share.
        IconButton(onClick = onShare, enabled = state.title.isNotBlank() || state.description.isNotBlank()) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
        }
    }
    IconButton(onClick = onTogglePin, modifier = pinModifier) {
        if (state.isPinned) {
            Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.action_unpin))
        } else {
            Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.action_pin))
        }
    }
    if (state.isEditing) {
        IconButton(onClick = onArchive) {
            if (state.isArchived) {
                Icon(Icons.Filled.Unarchive, contentDescription = stringResource(R.string.action_restore))
            } else {
                Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.action_archive))
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
        }
    }
    TextButton(onClick = onSave, enabled = state.canSave) {
        Text(stringResource(R.string.editor_save))
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
