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
import com.vintows.app.feature.assessments.domain.Assessment
import com.vintows.app.feature.assessments.ui.TestIntroDestination
import com.vintows.app.feature.assessments.ui.TestIntroRoute
import com.vintows.app.feature.assessments.ui.TestPlayerDestination
import com.vintows.app.feature.assessments.ui.TestPlayerRoute
import com.vintows.app.feature.courses.ui.ContentDestination
import com.vintows.app.feature.courses.ui.ContentRoute
import com.vintows.app.feature.courses.ui.CourseNodeDestination
import com.vintows.app.feature.courses.ui.CourseNodeRoute
import com.vintows.app.feature.foundation.FoundationRoute
import com.vintows.app.feature.gamification.ui.LeaderboardRoute
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

@Serializable
data object LeaderboardDestination

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
                onOpenLeaderboard = { navController.navigate(LeaderboardDestination) { launchSingleTop = true } },
                onOpenTest = { navController.navigate(it.toIntroDestination()) },
                onOpenProgram = { program ->
                    navController.navigate(
                        CourseNodeDestination(programId = program.id, levelCode = program.levelCode, nodeId = program.id, title = program.name),
                    )
                },
            )
        }
        composable<NotificationsDestination> {
            NotificationsRoute(onBack = { navController.popBackStack() }, pushEnabled = pushEnabled)
        }
        composable<LeaderboardDestination> {
            LeaderboardRoute(onBack = { navController.popBackStack() })
        }
        composable<CourseNodeDestination> {
            CourseNodeRoute(
                onBack = { navController.popBackStack() },
                onOpenNode = { navController.navigate(it) },
                onOpenContent = { navController.navigate(it) },
                onOpenTest = { navController.navigate(it.toIntroDestination()) },
            )
        }
        composable<TestIntroDestination> {
            TestIntroRoute(
                onBack = { navController.popBackStack() },
                onStart = { navController.navigate(it) { launchSingleTop = true } },
            )
        }
        composable<TestPlayerDestination> {
            TestPlayerRoute(onExit = { navController.popBackStack() })
        }
        composable<ContentDestination> {
            ContentRoute(onBack = { navController.popBackStack() })
        }
        composable<DiagnosticsDestination> {
            FoundationRoute(onBack = { navController.popBackStack() })
        }
    }
}

private fun Assessment.toIntroDestination() =
    TestIntroDestination(assessmentId = id, name = name, levelCode = levelCode, recordId = recordId)
