package com.abcamiletto.wortschatz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
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

private const val MAX_WIDTH = 560

@Serializable private data object HomeDestination
// The mode travels by name: enum route arguments need JVM reflection, which the website does not have.
@Serializable private data class SessionDestination(val mode: String)
@Serializable private data object LibraryDestination

@Composable
fun WortschatzRoot(container: AppContainer, onNavigationReady: suspend (NavController) -> Unit = {}) {
    WortschatzTheme {
        Surface(Modifier.fillMaxSize()) {
            // A phone fills this column. A desktop browser shows it centred instead of stretching the layout.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val navController = rememberNavController()
                NavHost(navController, startDestination = HomeDestination, modifier = Modifier.widthIn(max = MAX_WIDTH.dp)) {
                    composable<HomeDestination> {
                        HomeRoute(
                            container = container,
                            onStart = { navController.navigate(SessionDestination(it.name)) },
                            onBrowse = { navController.navigate(LibraryDestination) },
                        )
                    }
                    composable<SessionDestination> { entry ->
                        SessionRoute(
                            container = container,
                            mode = SessionMode.valueOf(entry.toRoute<SessionDestination>().mode),
                            onExit = { navController.popBackStack() },
                        )
                    }
                    composable<LibraryDestination> {
                        LibraryRoute(container = container, onBack = { navController.popBackStack() })
                    }
                }
                LaunchedEffect(navController) { onNavigationReady(navController) }
            }
        }
    }
}
