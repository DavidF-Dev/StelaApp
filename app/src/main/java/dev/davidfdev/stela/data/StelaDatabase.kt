package dev.davidfdev.stela.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class], version = 4, exportSchema = true)
abstract class StelaDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        // v2 adds the optional emoji column; existing rows default to no emoji.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN emoji TEXT NOT NULL DEFAULT ''")
            }
        }

        // v3 adds the archive flag; existing rows default to not archived.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v4 adds the nullable auto-pin / auto-unpin times; existing rows have neither.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN pinAt INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN unpinAt INTEGER")
            }
        }
    }
}
