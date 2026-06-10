package dev.davidfdev.stela.data

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupIoTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    // Exercises the real ContentResolver write/read on a file Uri, plus the codec, end to end.
    @Test
    fun write_thenRead_roundTripsBackupThroughContentResolver() = runBlocking {
        val notes = listOf(
            Note(title = "Backup", description = "body", emoji = "📦", createdAt = 1, updatedAt = 2),
        )
        val io = AndroidBackupIo(context.contentResolver)
        val uri = Uri.fromFile(File(context.cacheDir, "stela-test-backup.json"))

        io.write(uri, BackupCodec.encode(notes))
        val decoded = BackupCodec.decode(io.read(uri)).getOrThrow()

        assertEquals(1, decoded.size)
        assertEquals("Backup", decoded[0].title)
        assertEquals("📦", decoded[0].emoji)
        assertEquals(1L, decoded[0].createdAt)
    }
}
