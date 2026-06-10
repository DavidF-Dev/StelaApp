package dev.davidfdev.stela

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dev.davidfdev.stela.settings.Settings
import dev.davidfdev.stela.settings.ThemeMode
import dev.davidfdev.stela.ui.StelaNavHost
import dev.davidfdev.stela.ui.StelaTheme
import dev.davidfdev.stela.ui.canPostNotifications
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    // Cold-started from a notification: finishing the editor returns home, not to an unvisited list.
    private val finishOnEditorDone = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as StelaApp).container

        finishOnEditorDone.value = if (savedInstanceState != null) {
            savedInstanceState.getBoolean(KEY_FINISH_ON_EDITOR_DONE)
        } else {
            isNotificationDeepLink(intent.action, intent.data?.scheme)
        }
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = Settings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }

            QuickAddOnboarding(quickAddEnabled = settings.quickAddEnabled, container = container)

            StelaTheme(darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val controller = rememberNavController()
                    SideEffect { navController = controller }
                    StelaNavHost(
                        navController = controller,
                        finishOnEditorDone = finishOnEditorDone.value,
                        onFinish = { finish() },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_FINISH_ON_EDITOR_DONE, finishOnEditorDone.value)
    }

    /// The launch intent's deep link is handled by NavHost automatically; a singleTop
    /// re-delivery (e.g. tapping Edit while the app is open) arrives here instead.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Warm re-delivery: the app was already open, so finishing the editor pops rather than ends the task.
        finishOnEditorDone.value = false
        navController?.handleDeepLink(intent)
    }

    private companion object {
        const val KEY_FINISH_ON_EDITOR_DONE = "finish_on_editor_done"
    }
}

/// Quick-add defaults on, so on first launch (API 33+) request POST_NOTIFICATIONS so
/// the service's quick-add notification can show; reconcile the service on grant.
@androidx.compose.runtime.Composable
private fun QuickAddOnboarding(quickAddEnabled: Boolean, container: dev.davidfdev.stela.di.AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var requested by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { container.notePinner.reconcileService() }
    }
    LaunchedEffect(quickAddEnabled) {
        if (!requested &&
            quickAddEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !canPostNotifications(context)
        ) {
            requested = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
