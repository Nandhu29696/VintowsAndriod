package com.vintows.app.feature.foundation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintows.app.BuildConfig
import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.ErrorKind
import com.vintows.app.core.network.HealthApi
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoundationUiState(
    val environment: String = BuildConfig.FLAVOR.uppercase(),
    val baseUrl: String = BuildConfig.BASE_URL,
    val version: String = BuildConfig.VERSION_NAME,
    val sessionSummary: String = "Checking…",
    val apiCheck: ApiCheck = ApiCheck.Idle,
)

sealed interface ApiCheck {
    data object Idle : ApiCheck
    data object Running : ApiCheck
    data class Reachable(val detail: String) : ApiCheck
    data class Unreachable(val detail: String) : ApiCheck
}

/** Debug-only diagnostics (the Phase 0 check screen), opened from Profile. */
@HiltViewModel
class FoundationViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val healthApi: HealthApi,
    private val apiCaller: ApiCaller,
) : ViewModel() {

    private val _state = MutableStateFlow(FoundationUiState())
    val state: StateFlow<FoundationUiState> = _state.asStateFlow()

    init {
        val session = sessionManager.current()
        _state.update {
            it.copy(sessionSummary = session?.let { s -> "${s.email} (${s.role ?: "no role"})" } ?: "Not logged in")
        }
    }

    fun checkApi() {
        _state.update { it.copy(apiCheck = ApiCheck.Running) }
        viewModelScope.launch {
            val result = apiCaller.call { healthApi.supportCategories() }
            val check = when {
                result is NetworkResult.Success ->
                    ApiCheck.Reachable("Authenticated request succeeded")
                result is NetworkResult.Error && result.kind == ErrorKind.Unauthorized ->
                    // Expected without a token: proves the server and error parsing both work.
                    ApiCheck.Reachable("HTTP 401 · \"${result.message}\"")
                result is NetworkResult.Error ->
                    ApiCheck.Unreachable("${result.kind} · ${result.message}")
                else -> ApiCheck.Unreachable("Unknown result")
            }
            _state.update { it.copy(apiCheck = check) }
        }
    }
}
