package dev.davidfdev.stela.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /// All notes, most-recently-updated first.
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    /// Flips only the pin flag. Pinning is not a content edit, so this deliberately
    /// leaves updatedAt untouched and the list order unchanged.
    @Query("UPDATE notes SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean)

    /// Flips only the archive flag. Archiving is not a content edit, so this deliberately
    /// leaves updatedAt untouched and the list order unchanged.
    @Query("UPDATE notes SET isArchived = :isArchived WHERE id = :id")
    suspend fun setArchived(id: Long, isArchived: Boolean)

    /// Sets a note's auto-pin / auto-unpin times (null clears). Scheduling is not a content edit,
    /// so this leaves updatedAt untouched and the list order unchanged.
    @Query("UPDATE notes SET pinAt = :pinAt, unpinAt = :unpinAt WHERE id = :id")
    suspend fun setSchedule(id: Long, pinAt: Long?, unpinAt: Long?)

    /// Clears only the auto-pin time — pinning a note fulfils any pending auto-pin.
    @Query("UPDATE notes SET pinAt = NULL WHERE id = :id")
    suspend fun clearPinAt(id: Long)

    /// Clears only the auto-unpin time — unpinning a note fulfils any pending auto-unpin.
    @Query("UPDATE notes SET unpinAt = NULL WHERE id = :id")
    suspend fun clearUnpinAt(id: Long)

    @Query("SELECT COUNT(*) FROM notes WHERE isPinned = 1")
    suspend fun countPinned(): Int

    /// Inserts a new note or updates an existing one. Returns the note's row id,
    /// which equals its generated [Note.id] for a freshly inserted note.
    @Upsert
    suspend fun upsert(note: Note): Long

    @Delete
    suspend fun delete(note: Note)
}
