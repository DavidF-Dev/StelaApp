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
) {
    companion object {
        // v1 ships a single silhouette; the column is defaulted so the v2 icon
        // set can be added without a schema migration.
        const val DEFAULT_ICON_ID = "default"
    }
}
