package dev.davidfdev.stela

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dev.davidfdev.stela.settings.Settings
import dev.davidfdev.stela.settings.ThemeMode
import dev.davidfdev.stela.ui.Routes
import dev.davidfdev.stela.ui.StelaNavHost
import dev.davidfdev.stela.ui.StelaTheme
import dev.davidfdev.stela.ui.editor.EditorViewModel
import dev.davidfdev.stela.ui.canPostNotifications
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var navController: NavHostController? = null

    // Cold-started from a notification: finishing the editor returns home, not to an unvisited list.
    private val finishOnEditorDone = mutableStateOf(false)

    // Expanded from the popup: finishing the editor lands the user on the list (they entered the app via
    // the popup), rather than finishing the task. Takes precedence over finishOnEditorDone.
    private val goToListOnEditorDone = mutableStateOf(false)

    // An existing-note Expand must open with nothing focused, but the reused window restores focus to its
    // last-focused field (re-raising the keyboard) as it regains focus. One-shot: clear it on that gain.
    private var clearFocusOnWindowFocusGain = false

    // A cold plain-text share: navigate to a new-note editor once the nav host is composed.
    private val pendingShareNavigation = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must precede super.onCreate so the splash theme is swapped for the app theme before the first frame.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as StelaApp).container

        if (savedInstanceState != null) {
            finishOnEditorDone.value = savedInstanceState.getBoolean(KEY_FINISH_ON_EDITOR_DONE)
            goToListOnEditorDone.value = savedInstanceState.getBoolean(KEY_GO_TO_LIST_ON_EDITOR_DONE)
        } else {
            val fromExpand = intent.getBooleanExtra(EXTRA_FROM_POPUP_EXPAND, false)
            // Expand from the popup uses the editor deep link but is in-app navigation: send the editor
            // to the list on done rather than finishing the task as a cold notification entry would.
            finishOnEditorDone.value =
                isEditorDeepLink(intent.action, intent.data?.scheme, intent.data?.path) && !fromExpand
            goToListOnEditorDone.value = fromExpand
        }
        clearFocusOnWindowFocusGain = isExpandOfExistingNote(intent)

        // A plain-text share opens a new note prefilled with the shared text. Guarded on a null saved
        // state so a recreation (e.g. rotation) doesn't re-handle the same share intent.
        if (savedInstanceState == null && isSendTextIntent(intent.action, intent.type)) {
            container.pendingDraft = sharedNoteDraft(
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            )
            pendingShareNavigation.value = true
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
                    // A cold share seeded the draft in onCreate; open the editor once the host is ready.
                    LaunchedEffect(Unit) {
                        if (pendingShareNavigation.value) {
                            pendingShareNavigation.value = false
                            controller.navigate(Routes.EDITOR_NEW)
                        }
                    }
                    StelaNavHost(
                        navController = controller,
                        finishOnEditorDone = finishOnEditorDone.value,
                        goToListOnEditorDone = goToListOnEditorDone.value,
                        onGoToListConsumed = { goToListOnEditorDone.value = false },
                        onFinish = { finish() },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_FINISH_ON_EDITOR_DONE, finishOnEditorDone.value)
        outState.putBoolean(KEY_GO_TO_LIST_ON_EDITOR_DONE, goToListOnEditorDone.value)
    }

    // The popup routes into this open app instead of floating over it. Started (not resumed) is the signal:
    // the transparent popup pauses this activity but leaves it started, so it reads visible at that moment.
    override fun onStart() {
        super.onStart()
        (application as StelaApp).container.isMainActivityVisible = true
    }

    override fun onStop() {
        super.onStop()
        (application as StelaApp).container.isMainActivityVisible = false
    }

    // Returning from the background is a normal re-open, so a cold-entered editor should no longer finish
    // the task on done. Downgrade to the in-app go-to-list exit (a bare pop could pop into an empty stack,
    // since the editor deep link's synthesised back stack may have no list under it). onRestart runs only
    // on re-entry, never on first launch, so the genuine cold session keeps its finish behaviour.
    override fun onRestart() {
        super.onRestart()
        if (finishOnEditorDone.value) {
            finishOnEditorDone.value = false
            goToListOnEditorDone.value = true
        }
    }

    /// The launch intent's deep link is handled by NavHost automatically; a singleTop
    /// re-delivery (e.g. tapping Edit while the app is open) arrives here instead.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isSendTextIntent(intent.action, intent.type)) {
            // A warm share: seed a new-note draft and open the editor on top of the current screen, which
            // is plain in-app navigation (finishing the editor pops back, not finishes the task).
            (application as StelaApp).container.pendingDraft = sharedNoteDraft(
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            )
            finishOnEditorDone.value = false
            goToListOnEditorDone.value = false
            navController?.navigate(Routes.EDITOR_NEW)
            return
        }
        // Warm re-delivery: the app was already open, so finishing the editor pops rather than ends the
        // task — unless it came from the popup's Expand, which lands the user on the list.
        goToListOnEditorDone.value = intent.getBooleanExtra(EXTRA_FROM_POPUP_EXPAND, false)
        finishOnEditorDone.value = false
        clearFocusOnWindowFocusGain = isExpandOfExistingNote(intent)
        // Skip re-navigating when the editor for this exact note is already on top (a warm re-entry from
        // its own notification while open); handleDeepLink would otherwise stack a duplicate editor.
        if (!isEditorAlreadyOpenFor(intent)) navController?.handleDeepLink(intent)
    }

    private fun isEditorAlreadyOpenFor(intent: Intent): Boolean {
        val current = navController?.currentBackStackEntry ?: return false
        if (current.destination.route != Routes.EDITOR_EDIT) return false
        val openId = current.arguments?.getLong(EditorViewModel.NOTE_ID_KEY) ?: return false
        val targetId = intent.data?.lastPathSegment?.toLongOrNull() ?: return false
        return openId == targetId
    }

    // An existing-note Expand: the from-popup-expand marker plus the /editor/{id} deep-link path (a new
    // note uses /new and legitimately auto-focuses its title, so it must not be cleared).
    private fun isExpandOfExistingNote(intent: Intent): Boolean =
        intent.getBooleanExtra(EXTRA_FROM_POPUP_EXPAND, false) &&
            intent.data?.path?.startsWith("/editor") == true

    // The window restores focus to its last-focused field as it regains focus; for an existing-note Expand
    // that re-raises the keyboard over an editor meant to open quiet. Clear it once, deferred past the
    // framework's own restore on this same focus gain.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && clearFocusOnWindowFocusGain) {
            clearFocusOnWindowFocusGain = false
            val decor = window.decorView
            decor.post {
                currentFocus?.clearFocus()
                WindowCompat.getInsetsController(window, decor).hide(WindowInsetsCompat.Type.ime())
            }
        }
    }

    companion object {
        /// Marks an editor deep link as coming from the popup's Expand (in-app), so the editor pops to
        /// the list on completion rather than finishing the task.
        const val EXTRA_FROM_POPUP_EXPAND = "dev.davidfdev.stela.extra.FROM_POPUP_EXPAND"

        private const val KEY_FINISH_ON_EDITOR_DONE = "finish_on_editor_done"
        private const val KEY_GO_TO_LIST_ON_EDITOR_DONE = "go_to_list_on_editor_done"
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
