package dev.davidfdev.stela

import android.content.Intent
import dev.davidfdev.stela.ui.editor.NoteDraft

/// True when an intent is a plain-text share (`ACTION_SEND` of `text/plain`) that Stela turns into a
/// new note. Other send subtypes (multiple items, non-text MIME) are not handled.
fun isSendTextIntent(action: String?, type: String?): Boolean =
    action == Intent.ACTION_SEND && type == "text/plain"

private const val TITLE_MAX_LENGTH = 80
private const val TITLE_TRUNCATE_LENGTH = 50

/// Splits shared text into a (title, description) pair. A short first line becomes the title; a
/// long or single-line text gets a truncated title with the full text as description.
internal fun splitSharedText(text: String): Pair<String, String> {
    val newlineIndex = text.indexOf('\n')
    val firstLine = (if (newlineIndex >= 0) text.substring(0, newlineIndex) else text).trim()

    return when {
        newlineIndex >= 0 && firstLine.length <= TITLE_MAX_LENGTH -> {
            firstLine to text.substring(newlineIndex + 1).trim()
        }
        text.length <= TITLE_MAX_LENGTH -> {
            text to ""
        }
        else -> {
            text.take(TITLE_TRUNCATE_LENGTH).trimEnd() + "…" to text
        }
    }
}

/// Builds the new-note draft for shared plain text. When a subject is present it becomes the title
/// and the body becomes the description. Without a subject, the shared text is split: a short first
/// line becomes the title (remaining lines become the description); long text gets a truncated title
/// with the full text preserved as the description.
fun sharedNoteDraft(subject: String?, text: String?): NoteDraft {
    val trimmedSubject = subject?.trim()?.takeIf { it.isNotEmpty() }
    val trimmedText = text?.trim().orEmpty()

    val (title, description) = if (trimmedSubject != null) {
        trimmedSubject to trimmedText
    } else if (trimmedText.isEmpty()) {
        "" to ""
    } else {
        splitSharedText(trimmedText)
    }

    return NoteDraft(
        noteId = null,
        title = title,
        description = description,
        emoji = "",
        pinOnSave = true,
    )
}
