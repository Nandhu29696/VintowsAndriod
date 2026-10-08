package com.vintows.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintows.app.core.session.SessionEvent
import com.vintows.app.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppState {
    /** Restoring the stored session; the system splash screen stays up. */
    data object Loading : AppState
    data class LoggedOut(val sessionExpired: Boolean) : AppState
    data object LoggedIn : AppState
}

/**
 * Decides Login vs Home from the session alone, so login, logout and
 * 401-expiry all go through the same path.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val restored = MutableStateFlow(false)
    private val expired = MutableStateFlow(false)

    val state: StateFlow<AppState> =
        combine(restored, sessionManager.session, expired) { isRestored, session, isExpired ->
            when {
                !isRestored -> AppState.Loading
                session != null -> AppState.LoggedIn
                else -> AppState.LoggedOut(sessionExpired = isExpired)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    init {
        viewModelScope.launch {
            sessionManager.events.collect { event ->
                if (event == SessionEvent.Expired) expired.value = true
            }
        }
        viewModelScope.launch {
            sessionManager.restore()
            restored.value = true
        }
    }

    fun onLoggedIn() {
        expired.value = false
    }
}
