package dev.davidfdev.stela.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class], version = 2, exportSchema = true)
abstract class StelaDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        // v2 adds the optional emoji column; existing rows default to no emoji.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN emoji TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
