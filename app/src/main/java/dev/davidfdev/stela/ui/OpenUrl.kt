package dev.davidfdev.stela.ui

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/// Opens a URL in the user's browser via ACTION_VIEW. Stela only hands the URL to the
/// OS, so this needs no INTERNET permission. Ignored if the device has no handler.
fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
