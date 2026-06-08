package dev.davidfdev.stela

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dev.davidfdev.stela.settings.Settings
import dev.davidfdev.stela.settings.ThemeMode
import dev.davidfdev.stela.ui.StelaNavHost
import dev.davidfdev.stela.ui.StelaTheme

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = (application as StelaApp).container.settingsRepository.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = Settings())
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

            StelaTheme(darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val controller = rememberNavController()
                    SideEffect { navController = controller }
                    StelaNavHost(controller)
                }
            }
        }
    }

    // The launch intent's deep link is handled by NavHost automatically; a singleTop
    // re-delivery (e.g. tapping Edit while the app is open) arrives here instead.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navController?.handleDeepLink(intent)
    }
}
