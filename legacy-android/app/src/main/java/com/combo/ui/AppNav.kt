package com.combo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.combo.feature.combo_list.ComboListScreen
import com.combo.feature.combo_viewer.ComboViewerScreen
import com.combo.feature.builder.ComboBuilderScreen
import com.combo.feature.library.LibraryScreen
import com.combo.feature.viewer.PhotoViewerScreen

/**
 * Маршруты навигации. У `builder` два необязательных аргумента: `comboId` (открыть
 * существующее комбо) и `photos` (id фото через запятую для нового комбо).
 */
object Routes {
    const val LIBRARY = "library"
    const val COMBOS = "combos"
    const val VIEWER = "viewer/{photoId}"
    const val BUILDER = "builder?comboId={comboId}&photos={photos}"
    const val PLAYER = "player/{comboId}"

    fun viewer(photoId: String) = "viewer/$photoId"
    fun builder(comboId: String?, photos: List<String> = emptyList()): String {
        val c = comboId?.let { "comboId=$it" } ?: ""
        val p = if (photos.isNotEmpty()) "photos=${photos.joinToString(",")}" else ""
        val query = listOf(c, p).filter { it.isNotBlank() }.joinToString("&")
        return if (query.isEmpty()) "builder" else "builder?$query"
    }
    fun player(comboId: String) = "player/$comboId"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute == Routes.LIBRARY || currentRoute == Routes.COMBOS) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.LIBRARY,
                        onClick = { nav.navigateTop(Routes.LIBRARY) },
                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                        label = { Text("Photos") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.COMBOS,
                        onClick = { nav.navigateTop(Routes.COMBOS) },
                        icon = { Icon(Icons.Default.CollectionsBookmark, contentDescription = null) },
                        label = { Text("Combos") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenViewer = { id -> nav.navigate(Routes.viewer(id)) },
                    onCreateCombo = { ids -> nav.navigate(Routes.builder(null, ids)) },
                )
            }
            composable(Routes.COMBOS) {
                ComboListScreen(
                    onOpenBuilder = { id -> nav.navigate(Routes.builder(id)) },
                    onCreateCombo = { nav.navigate(Routes.builder(null)) },
                    onPlay = { id -> nav.navigate(Routes.player(id)) },
                )
            }
            composable(
                Routes.VIEWER,
                arguments = listOf(navArgument("photoId") { type = NavType.StringType }),
            ) { entry ->
                PhotoViewerScreen(
                    photoId = entry.arguments?.getString("photoId") ?: "",
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                Routes.BUILDER,
                arguments = listOf(
                    navArgument("comboId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("photos") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                ComboBuilderScreen(onBack = { nav.popBackStack() })
            }
            composable(
                Routes.PLAYER,
                arguments = listOf(navArgument("comboId") { type = NavType.StringType }),
            ) {
                ComboViewerScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
