package dev.davidfdev.stela.pin

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/// Starts and stops the pin service. Abstracted so the pin flow's start/stop
/// decision can be unit-tested without the Android service runtime.
interface ServiceController {
    fun start()
    fun stop()
}

class PinServiceController(private val context: Context) : ServiceController {
    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, PinService::class.java))
    }

    override fun stop() {
        context.stopService(Intent(context, PinService::class.java))
    }
}
