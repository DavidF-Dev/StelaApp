package dev.davidfdev.stela.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.davidfdev.stela.R
import dev.davidfdev.stela.resilience.DeviceResilience
import dev.davidfdev.stela.ui.SectionHeader
import dev.davidfdev.stela.settings.Settings
import dev.davidfdev.stela.settings.ThemeMode
import dev.davidfdev.stela.ui.openAppNotificationSettings
import dev.davidfdev.stela.ui.rememberNotificationPermissionGate
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class OemSettingsDialog { Battery, Autostart }

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val quickAddBlockedMessage = stringResource(R.string.snackbar_quick_add_needs_notifications)
    val settingsAction = stringResource(R.string.action_settings)
    val gate = rememberNotificationPermissionGate(
        onDenied = {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = quickAddBlockedMessage,
                    actionLabel = settingsAction,
                )
                if (result == SnackbarResult.ActionPerformed) openAppNotificationSettings(context)
            }
        },
    )

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.export(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.import(it) } }

    val exportDone = stringResource(R.string.snackbar_export_done)
    val exportFailed = stringResource(R.string.snackbar_export_failed)
    val importFailed = stringResource(R.string.snackbar_import_failed)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                BackupEvent.Exported -> exportDone
                is BackupEvent.Imported ->
                    context.resources.getQuantityString(R.plurals.snackbar_import_done, event.count, event.count)
                BackupEvent.ExportFailed -> exportFailed
                BackupEvent.ImportFailed -> importFailed
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    var batteryExempt by remember { mutableStateOf(DeviceResilience.isIgnoringBatteryOptimizations(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        batteryExempt = DeviceResilience.isIgnoringBatteryOptimizations(context)
    }
    val autostartIntent = remember { DeviceResilience.autostartIntent() }
    // Shown for any known aggressive-OEM target; the dialog handles a stale component.
    val autostartAvailable = autostartIntent != null

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        batteryExempt = batteryExempt,
        autostartAvailable = autostartAvailable,
        onThemeModeChange = viewModel::setThemeMode,
        onHideOnLockScreenChange = viewModel::setHideOnLockScreen,
        onSwipeToUnpinChange = viewModel::setSwipeToUnpin,
        onQuickAddEnabledChange = { enabled ->
            if (enabled) gate { viewModel.setQuickAddEnabled(true) } else viewModel.setQuickAddEnabled(false)
        },
        onOpenBatterySettings = {
            runCatching { context.startActivity(DeviceResilience.batteryOptimizationSettingsIntent()) }
        },
        onOpenAutostart = { autostartIntent?.let { intent -> runCatching { context.startActivity(intent) } } },
        onExport = { exportLauncher.launch("stela-backup-${LocalDate.now()}.json") },
        onImport = { importLauncher.launch(arrayOf("application/json")) },
        onOpenAbout = onOpenAbout,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: Settings,
    snackbarHostState: SnackbarHostState,
    batteryExempt: Boolean,
    autostartAvailable: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onHideOnLockScreenChange: (Boolean) -> Unit,
    onSwipeToUnpinChange: (Boolean) -> Unit,
    onQuickAddEnabledChange: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAutostart: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    var oemDialog by remember { mutableStateOf<OemSettingsDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_section_theme))
            ThemeMode.entries.forEach { mode ->
                ThemeOptionRow(
                    label = stringResource(themeModeLabelRes(mode)),
                    selected = state.themeMode == mode,
                    onSelect = { onThemeModeChange(mode) },
                )
            }

            SectionHeader(stringResource(R.string.settings_section_notifications))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_quick_add_title)) },
                supportingContent = { Text(stringResource(R.string.settings_quick_add_summary)) },
                trailingContent = {
                    Switch(checked = state.quickAddEnabled, onCheckedChange = onQuickAddEnabledChange)
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_hide_lock_title)) },
                supportingContent = { Text(stringResource(R.string.settings_hide_lock_summary)) },
                trailingContent = {
                    Switch(checked = state.hideOnLockScreen, onCheckedChange = onHideOnLockScreenChange)
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_swipe_unpin_title)) },
                supportingContent = { Text(stringResource(R.string.settings_swipe_unpin_summary)) },
                trailingContent = {
                    Switch(checked = state.swipeToUnpin, onCheckedChange = onSwipeToUnpinChange)
                },
            )

            SectionHeader(stringResource(R.string.settings_section_keep_alive))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_battery_title)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (batteryExempt) R.string.settings_battery_exempt else R.string.settings_battery_not_exempt,
                        ),
                    )
                },
                modifier = Modifier.clickable { oemDialog = OemSettingsDialog.Battery },
            )
            if (autostartAvailable) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_autostart_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_autostart_summary)) },
                    modifier = Modifier.clickable { oemDialog = OemSettingsDialog.Autostart },
                )
            }

            SectionHeader(stringResource(R.string.settings_section_backup))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export_title)) },
                supportingContent = { Text(stringResource(R.string.settings_export_summary)) },
                modifier = Modifier.clickable(onClick = onExport),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import_title)) },
                supportingContent = { Text(stringResource(R.string.settings_import_summary)) },
                modifier = Modifier.clickable(onClick = onImport),
            )

            SectionHeader(stringResource(R.string.settings_section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about_title)) },
                modifier = Modifier.clickable(onClick = onOpenAbout),
            )
        }
    }

    when (oemDialog) {
        OemSettingsDialog.Battery -> OemGuidanceDialog(
            title = stringResource(R.string.settings_battery_title),
            steps = stringResource(R.string.settings_battery_dialog_body),
            onOpenSettings = { oemDialog = null; onOpenBatterySettings() },
            onDismiss = { oemDialog = null },
        )
        OemSettingsDialog.Autostart -> OemGuidanceDialog(
            title = stringResource(R.string.settings_autostart_title),
            steps = stringResource(R.string.settings_autostart_dialog_body),
            onOpenSettings = { oemDialog = null; onOpenAutostart() },
            onDismiss = { oemDialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun OemGuidanceDialog(
    title: String,
    steps: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(steps)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.oem_dialog_caveat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.oem_dialog_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.oem_dialog_close)) }
        },
    )
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
    )
}

@StringRes
private fun themeModeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.SYSTEM -> R.string.theme_system
}
