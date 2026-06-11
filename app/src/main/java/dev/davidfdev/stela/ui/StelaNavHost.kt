package dev.davidfdev.stela.ui

import androidx.compose.runtime.Composable
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
    onFinish: () -> Unit = {},
) {
    val onEditorDone: () -> Unit = {
        if (finishOnEditorDone) onFinish() else navController.popBackStack()
    }
    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(
            route = Routes.LIST,
            deepLinks = listOf(
                navDeepLink { uriPattern = "${AndroidNotificationController.DEEP_LINK_BASE}/list" },
            ),
        ) {
            NoteListRoute(
                onAddNote = { navController.navigate(Routes.EDITOR_NEW) },
                onOpenNote = { id -> navController.navigate(Routes.editNote(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenArchived = { navController.navigate(Routes.ARCHIVED) },
            )
        }
        composable(Routes.ARCHIVED) {
            ArchivedRoute(
                onBack = { navController.popBackStack() },
                onOpenNote = { id -> navController.navigate(Routes.editNote(id)) },
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
        ) {
            EditorRoute(onDone = onEditorDone)
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
        ) {
            EditorRoute(onDone = onEditorDone)
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.ABOUT) {
            AboutRoute(onBack = { navController.popBackStack() })
        }
    }
}
