package com.vintows.app.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.component.EmptyState
import com.vintows.app.core.designsystem.component.InitialsAvatar
import com.vintows.app.core.designsystem.component.LoadingState
import com.vintows.app.core.rbac.HomeTab

@Composable
fun HomeRoute(
    onOpenNotifications: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onRetryMenus = { viewModel.loadMenus(forceRefresh = true) },
        onLogout = viewModel::logout,
        onOpenNotifications = onOpenNotifications,
        onOpenDiagnostics = onOpenDiagnostics,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetryMenus: () -> Unit,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    NotificationPermissionRequest(enabled = state.pushAvailable)
    val session = state.session
    if (session == null || state.tabs.isEmpty() || state.loggingOut) {
        LoadingState()
        return
    }

    var selectedName by rememberSaveable { mutableStateOf(state.tabs.first().name) }
    val selected = state.tabs.firstOrNull { it.name == selectedName } ?: state.tabs.first()
    var accountMenuOpen by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected.label) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
                    }
                    Box {
                        IconButton(onClick = { accountMenuOpen = true }) {
                            InitialsAvatar(session.email, color = MaterialTheme.colorScheme.secondary)
                        }
                        DropdownMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(session.email, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    session.role ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Log out") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                                onClick = {
                                    accountMenuOpen = false
                                    confirmLogout = true
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                state.tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { selectedName = tab.name },
                        icon = { Icon(tab.icon(), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                HomeTab.Modules -> ModulesTab(state.menus, onRetry = onRetryMenus)
                HomeTab.Profile -> ProfileTab(
                    session = session,
                    onLogout = { confirmLogout = true },
                    onOpenDiagnostics = onOpenDiagnostics,
                )
                HomeTab.Support -> ComingSoon("Support Centre", "Raise and track support requests. Coming in Phase 2.")
                HomeTab.Home -> ComingSoon("Your dashboard", "XP, streaks, badges and achievements. Coming in Phase 4.")
                HomeTab.Courses -> ComingSoon("Courses", "Browse programs, subjects and topics. Coming in Phase 5.")
                HomeTab.Tests -> ComingSoon("Tests", "Your assessments and results. Coming in Phase 6.")
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to sign in again to use Vintows.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }
}

/** Android 13+ needs runtime permission to show push notifications. Asked once per Home visit. */
@Composable
private fun NotificationPermissionRequest(enabled: Boolean) {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun ComingSoon(title: String, message: String) {
    EmptyState(title = title, message = message)
}

private fun HomeTab.icon(): ImageVector = when (this) {
    HomeTab.Home -> Icons.Outlined.Home
    HomeTab.Courses -> Icons.AutoMirrored.Outlined.MenuBook
    HomeTab.Tests -> Icons.Outlined.Quiz
    HomeTab.Support -> Icons.Outlined.HeadsetMic
    HomeTab.Modules -> Icons.Outlined.Apps
    HomeTab.Profile -> Icons.Outlined.Person
}
