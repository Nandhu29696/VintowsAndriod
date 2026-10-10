package com.vintows.app.feature.gamification.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.ui.Section
import com.vintows.app.core.ui.toSection
import com.vintows.app.feature.gamification.domain.Achievement
import com.vintows.app.feature.gamification.domain.Badge
import com.vintows.app.feature.gamification.domain.GamificationRepository
import com.vintows.app.feature.gamification.domain.Mission
import com.vintows.app.feature.gamification.domain.Progression
import com.vintows.app.feature.gamification.domain.Streak
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val progression: Section<Progression> = Section.Loading,
    val streaks: Section<List<Streak>> = Section.Loading,
    val missions: Section<List<Mission>> = Section.Loading,
    val badges: Section<List<Badge>> = Section.Loading,
    val achievements: Section<List<Achievement>> = Section.Loading,
    val refreshing: Boolean = false,
    /** Shown once as a snackbar when a refresh fails but older data is still on screen. */
    val transientError: String? = null,
)

/**
 * Loads the five dashboard sections in parallel. Each section has its own state, so one failing call
 * (e.g. missions) never hides the others.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: GamificationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        load(refresh = false)
    }

    fun refresh() {
        if (!_state.value.refreshing) load(refresh = true)
    }

    /** Reloads only the sections that failed. */
    fun retryFailed() = load(refresh = false, onlyFailed = true)

    fun consumeTransientError() = _state.update { it.copy(transientError = null) }

    private fun load(refresh: Boolean, onlyFailed: Boolean = false) {
        val current = _state.value
        fun wanted(section: Section<*>) = !onlyFailed || section is Section.Failed

        _state.update {
            if (refresh) {
                it.copy(refreshing = true)
            } else {
                it.copy(
                    progression = if (wanted(it.progression)) Section.Loading else it.progression,
                    streaks = if (wanted(it.streaks)) Section.Loading else it.streaks,
                    missions = if (wanted(it.missions)) Section.Loading else it.missions,
                    badges = if (wanted(it.badges)) Section.Loading else it.badges,
                    achievements = if (wanted(it.achievements)) Section.Loading else it.achievements,
                )
            }
        }

        viewModelScope.launch {
            coroutineScope {
                if (wanted(current.progression)) {
                    launch { apply(repository.progression()) { s, v -> s.copy(progression = v(s.progression)) } }
                }
                if (wanted(current.streaks)) {
                    launch { apply(repository.streaks()) { s, v -> s.copy(streaks = v(s.streaks)) } }
                }
                if (wanted(current.missions)) {
                    launch { apply(repository.missions()) { s, v -> s.copy(missions = v(s.missions)) } }
                }
                if (wanted(current.badges)) {
                    launch { apply(repository.badges()) { s, v -> s.copy(badges = v(s.badges)) } }
                }
                if (wanted(current.achievements)) {
                    launch { apply(repository.achievements()) { s, v -> s.copy(achievements = v(s.achievements)) } }
                }
            }
            _state.update { it.copy(refreshing = false) }
        }
    }

    /**
     * Stores one section's result. A failed refresh keeps the data already on screen and reports the error
     * as a snackbar instead of replacing the card with an error.
     */
    private fun <T> apply(
        result: NetworkResult<T>,
        set: (DashboardUiState, (Section<T>) -> Section<T>) -> DashboardUiState,
    ) {
        _state.update { state ->
            var error: String? = null
            val next = set(state) { old ->
                if (result is NetworkResult.Error && old is Section.Loaded) {
                    error = result.message
                    old
                } else {
                    result.toSection()
                }
            }
            if (error != null) next.copy(transientError = error) else next
        }
    }
}
