package dev.davidfdev.stela.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import dev.davidfdev.stela.notifications.AndroidNotificationController
import dev.davidfdev.stela.ui.about.AboutRoute
import dev.davidfdev.stela.ui.archived.ArchivedRoute
import dev.davidfdev.stela.ui.editor.EditorRoute
import dev.davidfdev.stela.ui.editor.EditorViewModel
import dev.davidfdev.stela.ui.notelist.NoteListRoute
import dev.davidfdev.stela.ui.settings.SettingsRoute

object Routes {
    const val LIST = "list"
    const val EDITOR_NEW = "editor"
    const val EDITOR_NEW_ROUTE = "editor?${EditorViewModel.PIN_KEY}={${EditorViewModel.PIN_KEY}}"
    const val EDITOR_EDIT = "editor/{${EditorViewModel.NOTE_ID_KEY}}"
    const val ARCHIVED = "archived"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun editNote(noteId: Long) = "editor/$noteId"
}

@Composable
fun StelaNavHost(
    navController: NavHostController = rememberNavController(),
    finishOnEditorDone: Boolean = false,
    goToListOnEditorDone: Boolean = false,
    onGoToListConsumed: () -> Unit = {},
    onFinish: () -> Unit = {},
) {
    // During a screen transition both the leaving and entering destinations stay composed and
    // hit-testable, so a tap can land on a screen that is visually on its way out. A full-screen overlay
    // swallows pointer input for the duration. It is armed synchronously at the navigation trigger — the
    // transition's own in-flight signal (more than one visible entry) lags the tap by a few frames, which
    // is exactly the window a quick follow-up tap would slip through — then released once that signal
    // reports the transition settled. Frame-driven, not timed, so it tracks the animation precisely.
    var blockNavInput by remember { mutableStateOf(false) }
    var navToken by remember { mutableIntStateOf(0) }
    val beginNavigation: () -> Unit = {
        blockNavInput = true
        navToken++
    }
    val goTo: (String) -> Unit = { route ->
        beginNavigation()
        navController.navigate(route)
    }
    LaunchedEffect(navToken) {
        if (navToken == 0) return@LaunchedEffect
        // Hold the overlay until the triggered transition has both started and finished. The frame caps
        // are a backstop so it can never stick if a trigger ever produces no transition.
        var frames = 0
        while (navController.visibleEntries.value.size <= 1 && frames < NAV_START_FRAME_CAP) {
            withFrameNanos {}
            frames++
        }
        frames = 0
        while (navController.visibleEntries.value.size > 1 && frames < NAV_SETTLE_FRAME_CAP) {
            withFrameNanos {}
            frames++
        }
        blockNavInput = false
    }

    val onEditorDone: () -> Unit = {
        when {
            // Expanded from the popup: the editor deep link's back stack has no list under it, so land on
            // the list explicitly (clearing the stack) rather than popping into nothing. One-shot — it
            // governs only the expanded editor, not later in-app navigation.
            goToListOnEditorDone -> {
                onGoToListConsumed()
                beginNavigation()
                navController.navigate(Routes.LIST) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
            finishOnEditorDone -> onFinish()
            else -> {
                beginNavigation()
                navController.popBackStack()
            }
        }
    }

    // Safety net: if a rapid back ever empties the host (no current destination), re-assert the list.
    LaunchedEffect(navController) {
        var hadDestination = false
        navController.currentBackStack.collect {
            if (navController.currentDestination != null) {
                hadDestination = true
            } else if (hadDestination) {
                navController.navigate(Routes.LIST) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    Box {
        NavHost(
            navController = navController,
            startDestination = Routes.LIST,
            enterTransition = { fadeIn(tween(TRANSITION_MS)) },
            exitTransition = { fadeOut(tween(TRANSITION_MS)) },
            popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
            popExitTransition = { fadeOut(tween(TRANSITION_MS)) },
        ) {
            composable(
                route = Routes.LIST,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "${AndroidNotificationController.DEEP_LINK_BASE}/list" },
                ),
            ) {
                NoteListRoute(
                    onAddNote = { goTo(Routes.EDITOR_NEW) },
                    onOpenNote = { id -> goTo(Routes.editNote(id)) },
                    onOpenSettings = { goTo(Routes.SETTINGS) },
                    onOpenArchived = { goTo(Routes.ARCHIVED) },
                )
            }
            composable(Routes.ARCHIVED) { entry ->
                val goBack = guardedPop(navController, entry, beginNavigation)
                BackHandler { goBack() }
                ArchivedRoute(
                    onBack = goBack,
                    onOpenNote = { id -> goTo(Routes.editNote(id)) },
                )
            }
            composable(
                route = Routes.EDITOR_NEW_ROUTE,
                arguments = listOf(
                    // New notes default to pinned; the editor's pin toggle can flip this before saving.
                    navArgument(EditorViewModel.PIN_KEY) { type = NavType.BoolType; defaultValue = true },
                ),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern =
                            "${AndroidNotificationController.DEEP_LINK_BASE}/new?${EditorViewModel.PIN_KEY}={${EditorViewModel.PIN_KEY}}"
                    },
                ),
            ) { entry ->
                // Every editor exit (back arrow, save, delete, system back) routes through onDone; ignore a
                // tap landing mid-transition so it can't pop a second destination and leave a blank host.
                EditorRoute(onDone = { if (entry.lifecycle.isResumed()) onEditorDone() })
            }
            composable(
                route = Routes.EDITOR_EDIT,
                arguments = listOf(navArgument(EditorViewModel.NOTE_ID_KEY) { type = NavType.LongType }),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern =
                            "${AndroidNotificationController.DEEP_LINK_BASE}/editor/{${EditorViewModel.NOTE_ID_KEY}}"
                    },
                ),
            ) { entry ->
                // Every editor exit (back arrow, save, delete, system back) routes through onDone; ignore a
                // tap landing mid-transition so it can't pop a second destination and leave a blank host.
                EditorRoute(onDone = { if (entry.lifecycle.isResumed()) onEditorDone() })
            }
            composable(Routes.SETTINGS) { entry ->
                val goBack = guardedPop(navController, entry, beginNavigation)
                BackHandler { goBack() }
                SettingsRoute(
                    onBack = goBack,
                    onOpenAbout = { goTo(Routes.ABOUT) },
                )
            }
            composable(Routes.ABOUT) { entry ->
                val goBack = guardedPop(navController, entry, beginNavigation)
                BackHandler { goBack() }
                AboutRoute(onBack = goBack)
            }
        }
        if (blockNavInput) {
            // Drawn after the NavHost so it sits above both transitioning destinations; consumes on the
            // initial pass so it wins before any child handler.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }
    }
}

/// Full-screen navigation cross-fade duration, in milliseconds.
private const val TRANSITION_MS = 350

/// Frames to wait for a triggered transition to begin (more than one visible entry) before giving up, so
/// the input overlay is never left armed by a trigger that produced no transition.
private const val NAV_START_FRAME_CAP = 12

/// Frames to wait for a running transition to settle before forcing the input overlay down, well beyond
/// the cross-fade so it lifts on settle in practice and only caps a pathologically long transition.
private const val NAV_SETTLE_FRAME_CAP = 60

/// True only when this lifecycle is fully RESUMED — i.e. settled, not mid navigation transition.
private fun Lifecycle.isResumed() = currentState == Lifecycle.State.RESUMED

/// Builds a back action that pops only while [entry] is RESUMED, so a rapid second press landing during
/// the pop transition is ignored rather than popping a further destination into a blank host. Invokes
/// [beginNavigation] only when the pop actually proceeds.
private fun guardedPop(
    navController: NavController,
    entry: NavBackStackEntry,
    beginNavigation: () -> Unit,
): () -> Unit = {
    if (entry.lifecycle.isResumed()) {
        beginNavigation()
        navController.popBackStack()
    }
}
