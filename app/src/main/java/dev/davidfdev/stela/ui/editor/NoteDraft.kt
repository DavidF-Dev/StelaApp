package dev.davidfdev.stela.ui.editor

/// An in-progress, unsaved edit carried from the quick-note popup into the full editor when the user
/// taps Expand. Held process-side (not encoded into nav arguments) so arbitrary text crosses cleanly.
/// [noteId] is null for a new note; [pinOnSave] seeds a new note's pin intent.
data class NoteDraft(
    val noteId: Long?,
    val title: String,
    val description: String,
    val emoji: String,
    val pinOnSave: Boolean,
)
