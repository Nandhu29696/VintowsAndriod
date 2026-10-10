package com.vintows.app.feature.gamification.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.session.SessionManager
import com.vintows.app.core.ui.Section
import com.vintows.app.core.ui.toSection
import com.vintows.app.feature.gamification.data.ALL_TIME_PERIOD
import com.vintows.app.feature.gamification.data.OVERALL_BOARD
import com.vintows.app.feature.gamification.domain.GamificationRepository
import com.vintows.app.feature.gamification.domain.LeaderboardEntry
import com.vintows.app.feature.gamification.domain.LeaderboardFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaderboardUiState(
    val filters: Section<LeaderboardFilters> = Section.Loading,
    val selectedType: String = OVERALL_BOARD,
    val selectedPeriod: String = ALL_TIME_PERIOD,
    val entries: Section<List<LeaderboardEntry>> = Section.Loading,
    /** The signed-in learner's id, to highlight their own row. */
    val myId: String? = null,
) {
    val myEntry: LeaderboardEntry? get() = (entries as? Section.Loaded)?.data?.firstOrNull { it.learnerId != null && it.learnerId == myId }
}

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val repository: GamificationRepository,
    private val savedState: SavedStateHandle,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LeaderboardUiState(
            selectedType = savedState[KEY_TYPE] ?: OVERALL_BOARD,
            selectedPeriod = savedState[KEY_PERIOD] ?: ALL_TIME_PERIOD,
            myId = sessionManager.current()?.userId,
        ),
    )
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    private var scoresJob: Job? = null

    init {
        loadFilters()
    }

    fun retry() {
        if (_state.value.filters is Section.Failed) loadFilters() else loadScores()
    }

    fun selectType(key: String) {
        if (key == _state.value.selectedType) return
        savedState[KEY_TYPE] = key
        _state.update { it.copy(selectedType = key) }
        loadScores()
    }

    fun selectPeriod(key: String) {
        if (key == _state.value.selectedPeriod) return
        savedState[KEY_PERIOD] = key
        _state.update { it.copy(selectedPeriod = key) }
        loadScores()
    }

    private fun loadFilters() {
        _state.update { it.copy(filters = Section.Loading, entries = Section.Loading) }
        viewModelScope.launch {
            when (val result = repository.leaderboardFilters()) {
                is NetworkResult.Success -> {
                    val filters = result.data
                    _state.update { s ->
                        // A saved choice the admin has since switched off falls back to the first option.
                        s.copy(
                            filters = Section.Loaded(filters),
                            selectedType = s.selectedType.takeIf { t -> filters.types.any { it.key == t } } ?: filters.types.first().key,
                            selectedPeriod = s.selectedPeriod.takeIf { p -> filters.periods.any { it.key == p } } ?: filters.periods.first().key,
                        )
                    }
                    loadScores()
                }
                is NetworkResult.Error -> _state.update { it.copy(filters = Section.Failed(result.message)) }
            }
        }
    }

    private fun loadScores() {
        scoresJob?.cancel()
        _state.update { it.copy(entries = Section.Loading) }
        val type = _state.value.selectedType
        val period = _state.value.selectedPeriod
        scoresJob = viewModelScope.launch {
            val result = repository.leaderboard(type, period)
            _state.update { it.copy(entries = result.toSection()) }
        }
    }

    private companion object {
        const val KEY_TYPE = "leaderboard_type"
        const val KEY_PERIOD = "leaderboard_period"
    }
}
