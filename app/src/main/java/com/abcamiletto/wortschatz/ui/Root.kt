package com.abcamiletto.wortschatz.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.abcamiletto.wortschatz.AppContainer
import com.abcamiletto.wortschatz.ui.home.HomeRoute
import com.abcamiletto.wortschatz.ui.library.LibraryRoute
import com.abcamiletto.wortschatz.ui.session.SessionMode
import com.abcamiletto.wortschatz.ui.session.SessionRoute
import com.abcamiletto.wortschatz.ui.theme.WortschatzTheme
import kotlinx.serialization.Serializable

@Serializable private data object HomeDestination
@Serializable private data class SessionDestination(val mode: SessionMode)
@Serializable private data object LibraryDestination

@Composable
fun WortschatzRoot(container: AppContainer) {
    WortschatzTheme {
        Surface {
            val navController = rememberNavController()
            NavHost(navController, startDestination = HomeDestination) {
                composable<HomeDestination> {
                    HomeRoute(
                        container = container,
                        onStart = { navController.navigate(SessionDestination(it)) },
                        onBrowse = { navController.navigate(LibraryDestination) },
                    )
                }
                composable<SessionDestination> { entry ->
                    SessionRoute(
                        container = container,
                        mode = entry.toRoute<SessionDestination>().mode,
                        onExit = { navController.popBackStack() },
                    )
                }
                composable<LibraryDestination> {
                    LibraryRoute(container = container, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
