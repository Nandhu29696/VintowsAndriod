package com.vintows.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.theme.VintowsTheme
import com.vintows.app.navigation.VintowsNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { appViewModel.state.value == AppState.Loading }
        enableEdgeToEdge()
        setContent {
            val appState by appViewModel.state.collectAsStateWithLifecycle()
            VintowsTheme {
                VintowsNavHost(appState = appState, onLoggedIn = appViewModel::onLoggedIn)
            }
        }
    }
}
