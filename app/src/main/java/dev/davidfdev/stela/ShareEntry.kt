package dev.davidfdev.stela

import android.content.Intent
import dev.davidfdev.stela.ui.editor.NoteDraft

/// True when an intent is a plain-text share (`ACTION_SEND` of `text/plain`) that Stela turns into a
/// new note. Other send subtypes (multiple items, non-text MIME) are not handled.
fun isSendTextIntent(action: String?, type: String?): Boolean =
    action == Intent.ACTION_SEND && type == "text/plain"

/// Builds the new-note draft for shared plain text: the subject (when present) seeds the title and the
/// shared body seeds the description, both trimmed. The draft pins on save, as new notes do.
fun sharedNoteDraft(subject: String?, text: String?): NoteDraft =
    NoteDraft(
        noteId = null,
        title = subject?.trim().orEmpty(),
        description = text?.trim().orEmpty(),
        emoji = "",
        pinOnSave = true,
    )
