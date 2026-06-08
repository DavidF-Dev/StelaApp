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
        // Without notification permission the foreground notification can't show, so
        // starting is pointless; the UI requests permission and reconciles on grant.
        if (!canPostNotifications(context)) return
        ContextCompat.startForegroundService(context, Intent(context, PinService::class.java))
    }

    override fun stop() {
        context.stopService(Intent(context, PinService::class.java))
    }
}
