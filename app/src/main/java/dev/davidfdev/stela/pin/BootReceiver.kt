package dev.davidfdev.stela.pin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/// On boot, starts the pin service, which re-asserts pinned notes and self-stops if
/// there is nothing to keep alive. Best-effort: some OEMs withhold BOOT_COMPLETED
/// until the app is allowed to autostart.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ContextCompat.startForegroundService(context, Intent(context, PinService::class.java))
    }
}
