package dev.davidfdev.stela.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.davidfdev.stela.R
import dev.davidfdev.stela.resilience.DeviceResilience
import dev.davidfdev.stela.ui.canPostNotifications
import dev.davidfdev.stela.ui.openAppNotificationSettings
import dev.davidfdev.stela.ui.resilience.OemGuidanceDialog
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

private enum class KeepAliveDialog { Battery, Autostart }

/// The first-run flow: a three-pane pager (concept -> notifications -> keep-alive) shown over the app
/// until onboarding is complete. [onComplete] is called by Skip, Get started, or finishing the last
/// pane (the caller persists the completion). [onNotificationsGranted] runs once the notification
/// permission is granted, so the caller can reconcile the foreground service.
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onNotificationsGranted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })

    var notificationsGranted by remember { mutableStateOf(canPostNotifications(context)) }
    var batteryExempt by remember { mutableStateOf(DeviceResilience.isIgnoringBatteryOptimizations(context)) }
    // Re-read on resume so returning from a system settings screen reflects the new state.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsGranted = canPostNotifications(context)
        batteryExempt = DeviceResilience.isIgnoringBatteryOptimizations(context)
    }
    val autostartIntent = remember { DeviceResilience.autostartIntent() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted || canPostNotifications(context)
        if (granted) onNotificationsGranted()
    }
    val onAllowNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Pre-13 (or channel turned off): no runtime request exists, so point at the settings screen.
            openAppNotificationSettings(context)
        }
    }

    var keepAliveDialog by remember { mutableStateOf<KeepAliveDialog?>(null) }

    // Back steps to the previous pane; on the first pane it falls through to the activity (which closes
    // without completing, so onboarding shows again next launch).
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onComplete) { Text(stringResource(R.string.onboarding_skip)) }
                }
            },
            bottomBar = {
                OnboardingBottomBar(
                    currentPage = pagerState.currentPage,
                    onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    onComplete = onComplete,
                )
            },
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> OnboardingPane(
                        icon = Icons.Filled.PushPin,
                        title = stringResource(R.string.onboarding_welcome_title),
                        body = stringResource(R.string.onboarding_welcome_body),
                    )

                    1 -> OnboardingPane(
                        icon = Icons.Filled.Notifications,
                        title = stringResource(R.string.onboarding_notifications_title),
                        body = stringResource(R.string.onboarding_notifications_body),
                    ) {
                        Spacer(Modifier.height(24.dp))
                        if (notificationsGranted) {
                            DoneRow(stringResource(R.string.onboarding_notifications_granted))
                        } else {
                            Button(onClick = onAllowNotifications) {
                                Text(stringResource(R.string.onboarding_notifications_action))
                            }
                        }
                    }

                    2 -> OnboardingPane(
                        icon = Icons.Filled.Bolt,
                        title = stringResource(R.string.onboarding_keepalive_title),
                        body = stringResource(R.string.onboarding_keepalive_body),
                    ) {
                        Spacer(Modifier.height(24.dp))
                        if (batteryExempt) {
                            DoneRow(stringResource(R.string.settings_battery_exempt))
                        } else {
                            Button(onClick = { keepAliveDialog = KeepAliveDialog.Battery }) {
                                Text(stringResource(R.string.onboarding_keepalive_battery_action))
                            }
                        }
                        // Only known aggressive OEMs have an auto-start target; others (stock, Samsung) get none.
                        if (autostartIntent != null) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { keepAliveDialog = KeepAliveDialog.Autostart }) {
                                Text(stringResource(R.string.onboarding_keepalive_autostart_action))
                            }
                        }
                    }
                }
            }
        }
    }

    when (keepAliveDialog) {
        KeepAliveDialog.Battery -> OemGuidanceDialog(
            title = stringResource(R.string.settings_battery_title),
            steps = stringResource(R.string.settings_battery_dialog_body),
            onOpenSettings = {
                keepAliveDialog = null
                runCatching { context.startActivity(DeviceResilience.batteryOptimizationSettingsIntent()) }
            },
            onDismiss = { keepAliveDialog = null },
        )
        KeepAliveDialog.Autostart -> OemGuidanceDialog(
            title = stringResource(R.string.settings_autostart_title),
            steps = stringResource(R.string.settings_autostart_dialog_body),
            onOpenSettings = {
                keepAliveDialog = null
                autostartIntent?.let { intent -> runCatching { context.startActivity(intent) } }
            },
            onDismiss = { keepAliveDialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun OnboardingPane(
    icon: ImageVector,
    title: String,
    body: String,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        actions()
    }
}

/// A "done" status line (checkmark + label) shown when a step's permission/exemption is already granted.
@Composable
private fun DoneRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OnboardingBottomBar(currentPage: Int, onNext: () -> Unit, onComplete: () -> Unit) {
    val lastPage = currentPage == PAGE_COUNT - 1
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        PageIndicator(currentPage = currentPage)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (lastPage) onComplete() else onNext() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (lastPage) R.string.onboarding_get_started else R.string.onboarding_next))
        }
    }
}

@Composable
private fun PageIndicator(currentPage: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PAGE_COUNT) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (selected) 10.dp else 8.dp)
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}
