package com.abcamiletto.wortschatz.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hasRoute
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

@Serializable private data object HomeDestination
// The mode travels by name: enum route arguments need JVM reflection, which the website does not have.
@Serializable private data class SessionDestination(val mode: String)
@Serializable private data object LibraryDestination

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
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= WIDE_FROM
                CompositionLocalProvider(LocalWide provides wide) {
                    val navController = rememberNavController()
                    val destination = navController.currentBackStackEntryAsState().value?.destination
                    val onHome = destination?.hasRoute<HomeDestination>() == true
                    val onWords = destination?.hasRoute<LibraryDestination>() == true
                    val status = sync?.status?.collectAsState()?.value
                    fun goHome() {
                        navController.popBackStack<HomeDestination>(inclusive = false)
                    }
                    Row(Modifier.fillMaxSize()) {
                        // A session is a focused task, so it takes the whole window.
                        if (wide && (onHome || onWords)) {
                            Sidebar(onHome, onWords, status, ::goHome) {
                                navController.navigate(LibraryDestination) { launchSingleTop = true }
                            }
                        }
                        NavHost(
                            navController,
                            startDestination = HomeDestination,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
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
                    }
                    LaunchedEffect(navController) { onNavigationReady(navController) }
                }
            }
        }
    }
}
