package dev.davidfdev.stela.data

import android.net.Uri

/// In-memory [BackupIo] for JVM tests: captures the last written text and replays a set one.
class FakeBackupIo(var toRead: String = "") : BackupIo {
    var written: String? = null
    override suspend fun write(uri: Uri, text: String) { written = text }
    override suspend fun read(uri: Uri): String = toRead
}
