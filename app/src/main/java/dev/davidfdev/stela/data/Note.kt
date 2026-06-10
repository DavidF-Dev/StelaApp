package dev.davidfdev.stela.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/// A single note. The notification ID for a pinned note is derived deterministically
/// from [id], so a note always maps to the same notification.
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val iconId: String = DEFAULT_ICON_ID,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val emoji: String = "",
) {
    companion object {
        // Defaulted so a future icon set can be added without a schema migration.
        const val DEFAULT_ICON_ID = "default"
    }
}

/// The title as shown to the user: the optional emoji prefixed to the title with a space,
/// or the title alone when no emoji is set.
fun displayTitle(emoji: String, title: String): String =
    if (emoji.isBlank()) title else "$emoji $title"

/// This note's title with its emoji prefix applied (see [displayTitle]).
val Note.displayTitle: String
    get() = displayTitle(emoji, title)
