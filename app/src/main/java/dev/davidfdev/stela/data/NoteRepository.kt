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

    /// Observes a single note as a [Flow], emitting on every change and `null` once it is deleted.
    /// Lets a screen stay consistent with changes made elsewhere (e.g. a background auto-pin).
    fun observeById(id: Long): Flow<Note?> = dao.observeById(id)

    /// Creates a new note, stamping [Note.createdAt] and [Note.updatedAt] to now.
    /// Returns the generated id.
    suspend fun create(
        title: String,
        description: String,
        iconId: String = Note.DEFAULT_ICON_ID,
        emoji: String = "",
        alertOnPin: Boolean = false,
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
                alertOnPin = alertOnPin,
            ),
        )
    }

    /// Persists edits to an existing note, bumping [Note.updatedAt] to now while
    /// preserving its original [Note.createdAt].
    suspend fun update(note: Note): Long = dao.upsert(note.copy(updatedAt = now()))

    /// Persists content edits (title/description/emoji) only, bumping [Note.updatedAt] to now. Unlike
    /// the flag/schedule setters this advances the modified time, but it leaves pin/archive/schedule
    /// columns untouched — so a content save can't revert a change (e.g. a background auto-pin) made
    /// while the editor was open.
    suspend fun updateContent(noteId: Long, title: String, description: String, emoji: String) =
        dao.setContent(noteId, title, description, emoji, now())

    /// Sets a note's pin flag without bumping updatedAt — pinning is not a content
    /// edit and must not reorder the list.
    suspend fun setPinned(noteId: Long, isPinned: Boolean) = dao.setPinned(noteId, isPinned)

    /// Sets a note's archive flag without bumping updatedAt — archiving is not a content
    /// edit and must not reorder the list.
    suspend fun setArchived(noteId: Long, isArchived: Boolean) = dao.setArchived(noteId, isArchived)

    /// Sets a note's auto-pin / auto-unpin times (null clears) without bumping updatedAt —
    /// scheduling is not a content edit and must not reorder the list.
    suspend fun setSchedule(noteId: Long, pinAt: Long?, unpinAt: Long?) =
        dao.setSchedule(noteId, pinAt, unpinAt)

    /// Clears a note's auto-pin time (pinning fulfils it).
    suspend fun clearPinAt(noteId: Long) = dao.clearPinAt(noteId)

    /// Clears a note's auto-unpin time (unpinning fulfils it).
    suspend fun clearUnpinAt(noteId: Long) = dao.clearUnpinAt(noteId)

    /// Sets a note's alert-on-pin flag without bumping updatedAt — it is not a content edit
    /// and must not reorder the list.
    suspend fun setAlertOnPin(noteId: Long, alertOnPin: Boolean) = dao.setAlertOnPin(noteId, alertOnPin)

    suspend fun countPinned(): Int = dao.countPinned()

    suspend fun delete(note: Note) = dao.delete(note)

    /// Re-inserts a note exactly as it was — preserving its id and timestamps — so an
    /// undo restores the original row rather than creating a new one.
    suspend fun restore(note: Note): Long = dao.upsert(note)

    /// Inserts imported notes as new rows: each gets a fresh id, so an import adds to the
    /// library without ever overwriting existing notes. Other fields are kept as given.
    suspend fun importNotes(notes: List<Note>): Int {
        notes.forEach { dao.upsert(it.copy(id = 0)) }
        return notes.size
    }
}
