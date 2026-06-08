package dev.davidfdev.stela.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.davidfdev.stela.settings.Settings
import dev.davidfdev.stela.settings.ThemeMode
import dev.davidfdev.stela.ui.openAppNotificationSettings
import dev.davidfdev.stela.ui.rememberNotificationPermissionGate
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gate = rememberNotificationPermissionGate(
        onDenied = {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Notifications are off — quick-add needs them.",
                    actionLabel = "Settings",
                )
                if (result == SnackbarResult.ActionPerformed) openAppNotificationSettings(context)
            }
        },
    )

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onThemeModeChange = viewModel::setThemeMode,
        onHideOnLockScreenChange = viewModel::setHideOnLockScreen,
        onQuickAddEnabledChange = { enabled ->
            if (enabled) gate { viewModel.setQuickAddEnabled(true) } else viewModel.setQuickAddEnabled(false)
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: Settings,
    snackbarHostState: SnackbarHostState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onHideOnLockScreenChange: (Boolean) -> Unit,
    onQuickAddEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SectionHeader("Theme")
            ThemeMode.entries.forEach { mode ->
                ThemeOptionRow(
                    label = themeModeLabel(mode),
                    selected = state.themeMode == mode,
                    onSelect = { onThemeModeChange(mode) },
                )
            }

            SectionHeader("Notifications")
            ListItem(
                headlineContent = { Text("Quick-add notification") },
                supportingContent = { Text("A persistent entry to add a note from the tray.") },
                trailingContent = {
                    Switch(checked = state.quickAddEnabled, onCheckedChange = onQuickAddEnabledChange)
                },
            )
            ListItem(
                headlineContent = { Text("Hide on lock screen") },
                supportingContent = { Text("Hide pinned notes on a secure lock screen.") },
                trailingContent = {
                    Switch(checked = state.hideOnLockScreen, onCheckedChange = onHideOnLockScreenChange)
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
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

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "Follow System"
}
