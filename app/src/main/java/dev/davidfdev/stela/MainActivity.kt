package dev.davidfdev.stela

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dev.davidfdev.stela.ui.StelaNavHost
import dev.davidfdev.stela.ui.StelaTheme

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StelaTheme {
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
