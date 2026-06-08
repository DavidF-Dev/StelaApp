package dev.davidfdev.stela.pin

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dev.davidfdev.stela.StelaApp
import dev.davidfdev.stela.notifications.AndroidNotificationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/// Foreground service that keeps the process alive while there is something to keep
/// alive. It hosts the quick-add notification and re-asserts pinned notes on start
/// (covering reboot and process restart).
class PinService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (applicationContext as StelaApp).container
        startInForeground(container.notificationController.buildQuickAddNotification())

        scope.launch {
            container.noteRepository.notes.first()
                .filter { it.isPinned }
                .forEach { container.notificationController.pin(it) }

            // Quick-add has no independent toggle yet (Phase 5), so the service only
            // needs to run while a note is pinned; if none, there is nothing to host.
            if (!ServiceLifecycle.shouldRun(container.noteRepository.countPinned(), quickAddEnabled = false)) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startInForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                AndroidNotificationController.QUICK_ADD_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(AndroidNotificationController.QUICK_ADD_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
