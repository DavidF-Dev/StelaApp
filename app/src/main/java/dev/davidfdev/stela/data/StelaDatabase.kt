package dev.davidfdev.stela.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Note::class], version = 1, exportSchema = true)
abstract class StelaDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
