package com.vintows.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vintows.app.AppState
import com.vintows.app.feature.auth.ui.LoginRoute
import com.vintows.app.feature.foundation.FoundationRoute
import com.vintows.app.feature.home.HomeRoute
import kotlinx.serialization.Serializable

@Serializable
data object LoginDestination

@Serializable
data object HomeDestination

/** Phase 0 check screen, reachable from Profile in debug builds. */
@Serializable
data object DiagnosticsDestination

@Composable
fun VintowsNavHost(
    appState: AppState,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (appState == AppState.Loading) return

    val navController = rememberNavController()
    val loggedIn = appState == AppState.LoggedIn
    val startDestination: Any = remember { if (loggedIn) HomeDestination else LoginDestination }

    // Login ↔ Home whenever the session appears or disappears (login, logout, 401 expiry).
    LaunchedEffect(loggedIn) {
        val target: Any = if (loggedIn) HomeDestination else LoginDestination
        val alreadyThere = navController.currentDestination?.hasRoute(target::class) == true
        if (!alreadyThere) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<LoginDestination> {
            LoginRoute(
                sessionExpired = (appState as? AppState.LoggedOut)?.sessionExpired == true,
                onLoggedIn = onLoggedIn,
            )
        }
        composable<HomeDestination> {
            HomeRoute(onOpenDiagnostics = { navController.navigate(DiagnosticsDestination) })
        }
        composable<DiagnosticsDestination> {
            FoundationRoute(onBack = { navController.popBackStack() })
        }
    }
}
