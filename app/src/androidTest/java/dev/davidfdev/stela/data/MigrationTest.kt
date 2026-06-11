package dev.davidfdev.stela.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StelaDatabase::class.java,
    )

    @Test
    fun migrate1To2_addsEmptyEmoji_andPreservesRows() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO notes (title, description, iconId, isPinned, createdAt, updatedAt) " +
                    "VALUES ('Old note', 'body', 'default', 1, 1000, 2000)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, StelaDatabase.MIGRATION_1_2)

        db.query("SELECT title, isPinned, emoji FROM notes").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Old note", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("", cursor.getString(2))
        }
    }

    @Test
    fun migrate2To3_addsUnarchivedFlag_andPreservesRows() {
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL(
                "INSERT INTO notes (title, description, iconId, isPinned, createdAt, updatedAt, emoji) " +
                    "VALUES ('Old note', 'body', 'default', 1, 1000, 2000, '📌')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 3, true, StelaDatabase.MIGRATION_2_3)

        db.query("SELECT title, isPinned, emoji, isArchived FROM notes").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Old note", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("📌", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
