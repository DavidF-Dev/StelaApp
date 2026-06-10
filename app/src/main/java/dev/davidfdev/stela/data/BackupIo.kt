package dev.davidfdev.stela.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/// Reads and writes a backup document at a Storage Access Framework Uri. Abstracted so the
/// settings ViewModel stays testable without a real ContentResolver.
interface BackupIo {
    suspend fun write(uri: Uri, text: String)
    suspend fun read(uri: Uri): String
}

class AndroidBackupIo(private val contentResolver: ContentResolver) : BackupIo {

    override suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            ?: error("could not open the document for writing")
    }

    override suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("could not open the document for reading")
    }
}
