package dev.davidfdev.stela.data

import kotlinx.serialization.json.Json

/// Encodes and decodes the notes-backup JSON. Pure (no Android, no IO) so it is fully
/// unit-testable; the IO and file picking live in the settings layer.
object BackupCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        // Write the version and every field explicitly, so a backup file is self-describing.
        encodeDefaults = true
    }

    fun encode(notes: List<Note>): String =
        json.encodeToString(NotesBackup.serializer(), NotesBackup(notes = notes.map { it.toBackup() }))

    /// Parses backup JSON into notes. Returns a failure for malformed or non-backup input.
    /// Imported notes get a fresh id (so they never clobber existing rows) and come in
    /// unpinned (so a bulk import never floods the status bar); other fields are preserved.
    fun decode(text: String): Result<List<Note>> = runCatching {
        // Strip a leading UTF-8 byte-order mark; some editors prepend one and the parser chokes on it.
        json.decodeFromString(NotesBackup.serializer(), text.removePrefix("\uFEFF")).notes.map { it.toNote() }
    }
}

private fun Note.toBackup() = NoteBackup(
    title = title,
    description = description,
    emoji = emoji,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isPinned = isPinned,
)

private fun NoteBackup.toNote() = Note(
    title = title,
    description = description,
    emoji = emoji,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
