package dev.davidfdev.stela.pin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.davidfdev.stela.StelaApp

/// Re-asserts pinned notes after a reboot or an app update by starting the pin service
/// through the shared start seam (which self-stops if there is nothing to keep alive).
/// Best-effort: some OEMs withhold BOOT_COMPLETED until the app is allowed to autostart.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        (context.applicationContext as StelaApp).container.serviceController.start()
    }
}
