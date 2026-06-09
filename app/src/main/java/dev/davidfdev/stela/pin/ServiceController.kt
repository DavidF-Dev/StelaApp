package dev.davidfdev.stela.pin

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.davidfdev.stela.ui.canPostNotifications

/// Starts and stops the pin service. Abstracted so the pin flow's start/stop
/// decision can be unit-tested without the Android service runtime.
interface ServiceController {
    fun start()
    fun stop()
}

class PinServiceController(private val context: Context) : ServiceController {
    override fun start() {
        // No permission means the foreground notification can't show, so starting is pointless.
        if (!canPostNotifications(context)) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, PinService::class.java))
        } catch (e: IllegalStateException) {
            // A background start outside an FGS-start exemption throws on API 31+; nothing to recover.
        }
    }

    override fun stop() {
        context.stopService(Intent(context, PinService::class.java))
    }
}
