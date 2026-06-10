package dev.davidfdev.stela.data

import kotlinx.coroutines.flow.Flow

/// Single source of truth over [NoteDao]. The UI and the pin service both read and
/// mutate notes through this type. Timestamps are stamped here so callers never
/// set them directly; [now] is injectable to keep that logic unit-testable.
class NoteRepository(
    private val dao: NoteDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val notes: Flow<List<Note>> = dao.observeAll()

    suspend fun getById(id: Long): Note? = dao.getById(id)

    /// Creates a new note, stamping [Note.createdAt] and [Note.updatedAt] to now.
    /// Returns the generated id.
    suspend fun create(
        title: String,
        description: String,
        iconId: String = Note.DEFAULT_ICON_ID,
        emoji: String = "",
    ): Long {
        val timestamp = now()
        return dao.upsert(
            Note(
                title = title,
                description = description,
                iconId = iconId,
                emoji = emoji,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
    }

    /// Persists edits to an existing note, bumping [Note.updatedAt] to now while
    /// preserving its original [Note.createdAt].
    suspend fun update(note: Note): Long = dao.upsert(note.copy(updatedAt = now()))

    /// Sets a note's pin flag without bumping updatedAt — pinning is not a content
    /// edit and must not reorder the list.
    suspend fun setPinned(noteId: Long, isPinned: Boolean) = dao.setPinned(noteId, isPinned)

    suspend fun countPinned(): Int = dao.countPinned()

    suspend fun delete(note: Note) = dao.delete(note)
}
