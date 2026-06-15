package dev.davidfdev.stela.data

import kotlinx.serialization.Serializable

/// The exported backup file: a version (for forward-compatibility) and the notes. A DTO
/// kept separate from the Room [Note] entity so DB migrations can't change the file format.
@Serializable
data class NotesBackup(
    val version: Int = BACKUP_VERSION,
    val notes: List<NoteBackup>,
)

@Serializable
data class NoteBackup(
    val title: String,
    val description: String,
    val emoji: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val pinAt: Long? = null,
    val unpinAt: Long? = null,
    val alertOnPin: Boolean = false,
)

// v4 adds the alert-on-pin flag; v3 and earlier files lack it and decode as silent.
const val BACKUP_VERSION = 4
