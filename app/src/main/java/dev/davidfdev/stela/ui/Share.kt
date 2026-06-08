package dev.davidfdev.stela.ui

import android.content.Context
import android.content.Intent
import dev.davidfdev.stela.R

/// Shares a note's title and description as plain text via the system share sheet.
/// Stela only hands text to the OS, so this needs no INTERNET permission.
fun shareNote(context: Context, title: String, description: String) {
    val text = listOf(title, description).filter { it.isNotBlank() }.joinToString("\n\n")
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser_title)))
}
