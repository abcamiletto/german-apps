package com.abcamiletto.wortschatz.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import com.abcamiletto.wortschatz.ui.sync.SignInRoute
import com.abcamiletto.wortschatz.ui.theme.WortschatzTheme
import kotlinx.serialization.Serializable

private val READING_WIDTH = 560.dp

@Serializable private data object HomeDestination
// The mode travels by name: enum route arguments need JVM reflection, which the website does not have.
@Serializable private data class SessionDestination(val mode: String)
@Serializable private data object LibraryDestination

/** A phone fills the column. A desktop browser shows it centred instead of stretching the layout. */
@Composable
private fun ReadingColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = READING_WIDTH)) { content() }
    }
}

@Composable
fun WortschatzRoot(container: AppContainer, onNavigationReady: suspend (NavController) -> Unit = {}) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(container.sync) {
        container.sync?.let { sync -> lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { sync.run() } }
    }
    WortschatzTheme {
        Surface(Modifier.fillMaxSize()) {
            val sync = container.sync
            if (sync != null && sync.needsSignIn.collectAsState().value) {
                SignInRoute(sync)
                return@Surface
            }
            val navController = rememberNavController()
            NavHost(navController, startDestination = HomeDestination, modifier = Modifier.fillMaxSize()) {
                composable<HomeDestination> {
                    HomeRoute(
                        container = container,
                        onStart = { navController.navigate(SessionDestination(it.name)) },
                        onBrowse = { navController.navigate(LibraryDestination) },
                    )
                }
                composable<SessionDestination> { entry ->
                    ReadingColumn {
                        SessionRoute(
                            container = container,
                            mode = SessionMode.valueOf(entry.toRoute<SessionDestination>().mode),
                            onExit = { navController.popBackStack() },
                        )
                    }
                }
                composable<LibraryDestination> {
                    ReadingColumn { LibraryRoute(container = container, onBack = { navController.popBackStack() }) }
                }
            }
            LaunchedEffect(navController) { onNavigationReady(navController) }
        }
    }
}
