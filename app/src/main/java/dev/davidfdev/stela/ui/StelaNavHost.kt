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
import dev.davidfdev.stela.ui.editor.EditorRoute
import dev.davidfdev.stela.ui.editor.EditorViewModel
import dev.davidfdev.stela.ui.notelist.NoteListRoute
import dev.davidfdev.stela.ui.settings.SettingsScreen

object Routes {
    const val LIST = "list"
    const val EDITOR_NEW = "editor"
    const val EDITOR_EDIT = "editor/{${EditorViewModel.NOTE_ID_KEY}}"
    const val SETTINGS = "settings"

    fun editNote(noteId: Long) = "editor/$noteId"
}

@Composable
fun StelaNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            NoteListRoute(
                onAddNote = { navController.navigate(Routes.EDITOR_NEW) },
                onOpenNote = { id -> navController.navigate(Routes.editNote(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.EDITOR_NEW) {
            EditorRoute(onDone = { navController.popBackStack() })
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
            EditorRoute(onDone = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
