package dev.davidfdev.stela.pin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.davidfdev.stela.StelaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/// Re-asserts pinned notes after a reboot or an app update by starting the pin service
/// through the shared start seam (which self-stops if there is nothing to keep alive), and
/// re-arms scheduled pins (alarms don't survive a reboot). Best-effort: some OEMs withhold
/// BOOT_COMPLETED until the app is allowed to autostart.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val container = (context.applicationContext as StelaApp).container
        container.serviceController.start()
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                container.notePinner.reconcileAll()
            } finally {
                pending.finish()
            }
        }
    }
}
