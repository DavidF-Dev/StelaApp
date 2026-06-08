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

    @Query("SELECT COUNT(*) FROM notes WHERE isPinned = 1")
    suspend fun countPinned(): Int

    /// Inserts a new note or updates an existing one. Returns the note's row id,
    /// which equals its generated [Note.id] for a freshly inserted note.
    @Upsert
    suspend fun upsert(note: Note): Long

    @Delete
    suspend fun delete(note: Note)
}
