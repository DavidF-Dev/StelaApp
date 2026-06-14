package dev.davidfdev.stela.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val onEditorDone: () -> Unit = {
        when {
            // Expanded from the popup: the editor deep link's back stack has no list under it, so land on
            // the list explicitly (clearing the stack) rather than popping into nothing. One-shot — it
            // governs only the expanded editor, not later in-app navigation.
            goToListOnEditorDone -> {
                onGoToListConsumed()
                navController.navigate(Routes.LIST) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
            finishOnEditorDone -> onFinish()
            else -> navController.popBackStack()
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
            ScreenGate {
                NoteListRoute(
                    onAddNote = { navController.navigate(Routes.EDITOR_NEW) },
                    onOpenNote = { id -> navController.navigate(Routes.editNote(id)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenArchived = { navController.navigate(Routes.ARCHIVED) },
                )
            }
        }
        composable(Routes.ARCHIVED) { entry ->
            val goBack = guardedPop(navController, entry)
            BackHandler { goBack() }
            ScreenGate {
                ArchivedRoute(
                    onBack = goBack,
                    onOpenNote = { id -> navController.navigate(Routes.editNote(id)) },
                )
            }
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
            ScreenGate {
                // Every editor exit (back arrow, save, delete, system back) routes through onDone; ignore a
                // tap landing mid-transition so it can't pop a second destination and leave a blank host.
                EditorRoute(onDone = { if (entry.lifecycle.isResumed()) onEditorDone() })
            }
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
            ScreenGate {
                // Every editor exit (back arrow, save, delete, system back) routes through onDone; ignore a
                // tap landing mid-transition so it can't pop a second destination and leave a blank host.
                EditorRoute(onDone = { if (entry.lifecycle.isResumed()) onEditorDone() })
            }
        }
        composable(Routes.SETTINGS) { entry ->
            val goBack = guardedPop(navController, entry)
            BackHandler { goBack() }
            ScreenGate {
                SettingsRoute(
                    onBack = goBack,
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                )
            }
        }
        composable(Routes.ABOUT) { entry ->
            val goBack = guardedPop(navController, entry)
            BackHandler { goBack() }
            ScreenGate {
                AboutRoute(onBack = goBack)
            }
        }
    }
}

/// Full-screen navigation cross-fade duration, in milliseconds.
private const val TRANSITION_MS = 350

/// Wraps a destination's [content], dropping all pointer input while the destination is mid-transition
/// (entering or leaving). A tap landing during the cross-fade then cannot reach a screen that is not
/// settled — neither the one animating out nor the one not yet fully arrived.
@Composable
private fun AnimatedVisibilityScope.ScreenGate(content: @Composable () -> Unit) {
    val settled = transition.currentState == transition.targetState
    val gate = if (settled) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            // Consume on the initial pass so the gate wins before any child handler sees the event.
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    }
    Box(gate) { content() }
}

/// True only when this lifecycle is fully RESUMED — i.e. settled, not mid navigation transition.
private fun Lifecycle.isResumed() = currentState == Lifecycle.State.RESUMED

/// Builds a back action that pops only while [entry] is RESUMED, so a rapid second press landing during
/// the pop transition is ignored rather than popping a further destination into a blank host.
private fun guardedPop(navController: NavController, entry: NavBackStackEntry): () -> Unit = {
    if (entry.lifecycle.isResumed()) navController.popBackStack()
}
