package dev.davidfdev.stela.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/// Whether the app may currently post notifications. On API 33+ this is the
/// POST_NOTIFICATIONS grant; below that, whether notifications are enabled at all.
fun canPostNotifications(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

/// Returns a function that runs [action] immediately if notifications are allowed,
/// otherwise requests POST_NOTIFICATIONS first and runs it only on grant. [onDenied]
/// fires when the user refuses. Used to gate pinning behind the permission.
@Composable
fun rememberNotificationPermissionGate(onDenied: () -> Unit): (action: () -> Unit) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pending.value
        pending.value = null
        if (granted) action?.invoke() else onDenied()
    }
    return { action ->
        if (canPostNotifications(context)) {
            action()
        } else {
            pending.value = action
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/// Opens this app's notification settings, for when the user has denied the
/// permission and must re-enable it manually.
fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent)
}
