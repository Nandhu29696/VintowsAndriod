package com.vintows.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.push.PushRegistrar
import com.vintows.app.core.rbac.HomeTab
import com.vintows.app.core.rbac.MenuItem
import com.vintows.app.core.rbac.MenuRepository
import com.vintows.app.core.rbac.homeTabs
import com.vintows.app.core.session.Session
import com.vintows.app.core.session.SessionManager
import com.vintows.app.feature.auth.domain.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MenusState {
    data object Loading : MenusState
    data class Loaded(val items: List<MenuItem>) : MenusState
    data class Error(val message: String) : MenusState
}

data class HomeUiState(
    val session: Session? = null,
    val tabs: List<HomeTab> = emptyList(),
    val menus: MenusState = MenusState.Loading,
    val loggingOut: Boolean = false,
    /** Ask for the Android 13+ notification permission only when push can actually work. */
    val pushAvailable: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionManager: SessionManager,
    private val menuRepository: MenuRepository,
    private val authRepository: AuthRepository,
    push: PushRegistrar,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(pushAvailable = push.isAvailable))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // The session is restored before Home is shown; if it is missing the app shell navigates to Login.
        sessionManager.current()?.let { session ->
            _state.update { it.copy(session = session, tabs = session.homeTabs()) }
            loadMenus()
        }
    }

    fun loadMenus(forceRefresh: Boolean = false) {
        val roleId = _state.value.session?.roleId
        if (roleId == null) {
            _state.update { it.copy(menus = MenusState.Error("No role is assigned to this account.")) }
            return
        }
        _state.update { it.copy(menus = MenusState.Loading) }
        viewModelScope.launch {
            val menus = when (val result = menuRepository.menus(roleId, forceRefresh)) {
                is NetworkResult.Success -> MenusState.Loaded(result.data)
                is NetworkResult.Error -> MenusState.Error(result.message)
            }
            _state.update { it.copy(menus = menus) }
        }
    }

    /** Clearing the session makes the app shell navigate back to Login. */
    fun logout() {
        if (_state.value.loggingOut) return
        _state.update { it.copy(loggingOut = true) }
        viewModelScope.launch { authRepository.logout() }
    }
}
