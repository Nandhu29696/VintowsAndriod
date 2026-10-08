package com.vintows.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.theme.VintowsTheme
import com.vintows.app.core.push.NotificationHelper
import com.vintows.app.navigation.VintowsNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    /** True when opened by tapping a push notification. */
    private val openNotifications = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { appViewModel.state.value == AppState.Loading }
        // Every screen has a navy/blue top edge (login gradient, top app bars), so status-bar icons are always light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val appState by appViewModel.state.collectAsStateWithLifecycle()
            val openNotificationsRequested by openNotifications
            VintowsTheme {
                VintowsNavHost(
                    appState = appState,
                    onLoggedIn = appViewModel::onLoggedIn,
                    pushEnabled = appViewModel.pushAvailable,
                    openNotifications = openNotificationsRequested,
                    onDeepLinkHandled = { openNotifications.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(NotificationHelper.EXTRA_OPEN) == NotificationHelper.OPEN_NOTIFICATIONS) {
            openNotifications.value = true
        }
    }
}
