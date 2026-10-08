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
import com.vintows.app.feature.auth.ui.student.RegisterRoute
import com.vintows.app.feature.auth.ui.student.StudentSignInRoute
import com.vintows.app.feature.foundation.FoundationRoute
import com.vintows.app.feature.home.HomeRoute
import com.vintows.app.feature.notifications.ui.NotificationsRoute
import kotlinx.serialization.Serializable

@Serializable
data object LoginDestination

/** Student email-code sign in / sign up. */
@Serializable
data object StudentSignInDestination

/** New-student profile form; [email] has just been verified. */
@Serializable
data class RegisterDestination(val email: String)

@Serializable
data object HomeDestination

@Serializable
data object NotificationsDestination

/** Phase 0 check screen, reachable from Profile in debug builds. */
@Serializable
data object DiagnosticsDestination

@Composable
fun VintowsNavHost(
    appState: AppState,
    onLoggedIn: () -> Unit,
    pushEnabled: Boolean,
    /** Set when the app was opened from a push notification; cleared via [onDeepLinkHandled]. */
    openNotifications: Boolean,
    onDeepLinkHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (appState == AppState.Loading) return

    val navController = rememberNavController()
    val loggedIn = appState == AppState.LoggedIn
    val startDestination: Any = remember { if (loggedIn) HomeDestination else LoginDestination }

    // Login ↔ Home whenever the session appears or disappears (login, sign-up, logout, 401 expiry).
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

    LaunchedEffect(openNotifications, loggedIn) {
        if (openNotifications && loggedIn) {
            navController.navigate(NotificationsDestination) { launchSingleTop = true }
            onDeepLinkHandled()
        }
    }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<LoginDestination> {
            LoginRoute(
                sessionExpired = (appState as? AppState.LoggedOut)?.sessionExpired == true,
                onLoggedIn = onLoggedIn,
                onStudentSignIn = { navController.navigate(StudentSignInDestination) },
            )
        }
        composable<StudentSignInDestination> {
            StudentSignInRoute(
                onBack = { navController.popBackStack() },
                onNeedsRegistration = { email -> navController.navigate(RegisterDestination(email)) },
            )
        }
        composable<RegisterDestination> {
            RegisterRoute(onExit = { navController.popBackStack() })
        }
        composable<HomeDestination> {
            HomeRoute(
                onOpenNotifications = { navController.navigate(NotificationsDestination) { launchSingleTop = true } },
                onOpenDiagnostics = { navController.navigate(DiagnosticsDestination) },
            )
        }
        composable<NotificationsDestination> {
            NotificationsRoute(onBack = { navController.popBackStack() }, pushEnabled = pushEnabled)
        }
        composable<DiagnosticsDestination> {
            FoundationRoute(onBack = { navController.popBackStack() })
        }
    }
}
