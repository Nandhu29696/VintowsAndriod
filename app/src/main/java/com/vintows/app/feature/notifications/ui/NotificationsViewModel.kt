package com.vintows.app.feature.notifications.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.feature.notifications.data.AppNotification
import com.vintows.app.feature.notifications.data.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val items: List<AppNotification> = emptyList(),
    /** First load (full-screen spinner). */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 0,
    val hasNextPage: Boolean = false,
    /** Full-screen error when nothing is loaded yet. */
    val error: String? = null,
    /** Snackbar-style error when a refresh or next page fails. */
    val transientError: String? = null,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        load(page = 1, mode = Mode.Initial)
    }

    fun refresh() = load(page = 1, mode = Mode.Refresh)

    fun retry() = load(page = 1, mode = Mode.Initial)

    fun loadMore() {
        val s = _state.value
        if (s.hasNextPage && !s.loadingMore && !s.refreshing) load(page = s.page + 1, mode = Mode.More)
    }

    fun consumeTransientError() = _state.update { it.copy(transientError = null) }

    private enum class Mode { Initial, Refresh, More }

    private fun load(page: Int, mode: Mode) {
        _state.update {
            when (mode) {
                Mode.Initial -> it.copy(loading = true, error = null)
                Mode.Refresh -> it.copy(refreshing = true)
                Mode.More -> it.copy(loadingMore = true)
            }
        }
        viewModelScope.launch {
            when (val result = repository.page(page)) {
                is NetworkResult.Success -> _state.update {
                    it.copy(
                        items = if (mode == Mode.More) it.items + result.data.items else result.data.items,
                        page = result.data.page,
                        hasNextPage = result.data.hasNextPage,
                        loading = false, refreshing = false, loadingMore = false, error = null,
                    )
                }
                is NetworkResult.Error -> _state.update {
                    if (mode == Mode.Initial) {
                        it.copy(loading = false, error = result.message)
                    } else {
                        it.copy(refreshing = false, loadingMore = false, transientError = result.message)
                    }
                }
            }
        }
    }
}
