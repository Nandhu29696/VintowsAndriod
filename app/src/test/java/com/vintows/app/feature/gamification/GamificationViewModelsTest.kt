package com.vintows.app.feature.gamification

import androidx.lifecycle.SavedStateHandle
import com.vintows.app.core.network.ErrorKind
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.session.Clock
import com.vintows.app.core.session.Session
import com.vintows.app.core.session.SessionManager
import com.vintows.app.core.ui.Section
import com.vintows.app.feature.gamification.data.humanizeKey
import com.vintows.app.feature.gamification.domain.Achievement
import com.vintows.app.feature.gamification.domain.Badge
import com.vintows.app.feature.gamification.domain.GamificationRepository
import com.vintows.app.feature.gamification.domain.GoalStatus
import com.vintows.app.feature.gamification.domain.LeaderboardEntry
import com.vintows.app.feature.gamification.domain.LeaderboardFilters
import com.vintows.app.feature.gamification.domain.LeaderboardOption
import com.vintows.app.feature.gamification.domain.Mission
import com.vintows.app.feature.gamification.domain.Progression
import com.vintows.app.feature.gamification.domain.Streak
import com.vintows.app.feature.gamification.ui.DashboardViewModel
import com.vintows.app.feature.gamification.ui.LeaderboardViewModel
import com.vintows.app.testing.FakeTokenCipher
import com.vintows.app.testing.InMemoryPreferencesDataStore
import com.vintows.app.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GamificationViewModelsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val error = NetworkResult.Error(ErrorKind.Server, "Server error. Please try again later.", 500)
    private val progression = Progression(1, "Beginner", 0, 0, 0, 100, "Learner", 0f)

    private class FakeRepository : GamificationRepository {
        var progression: NetworkResult<Progression> = NetworkResult.Success(Progression(2, "Learner", 150, 20, 0, 300, "Intermediate", 0.25f))
        var streaks: NetworkResult<List<Streak>> = NetworkResult.Success(emptyList())
        var missions: NetworkResult<List<Mission>> = NetworkResult.Success(emptyList())
        var badges: NetworkResult<List<Badge>> = NetworkResult.Success(emptyList())
        var achievements: NetworkResult<List<Achievement>> = NetworkResult.Success(emptyList())
        var filters: NetworkResult<LeaderboardFilters> = NetworkResult.Success(
            LeaderboardFilters(
                types = listOf(LeaderboardOption("typeOverall", "Overall"), LeaderboardOption("typeQuiz", "Quiz")),
                periods = listOf(LeaderboardOption("all_time", "All time"), LeaderboardOption("weekly", "Weekly")),
            ),
        )
        var scores: NetworkResult<List<LeaderboardEntry>> = NetworkResult.Success(emptyList())

        val calls = mutableListOf<String>()

        override suspend fun progression() = progression.also { calls += "progression" }
        override suspend fun streaks() = streaks.also { calls += "streaks" }
        override suspend fun missions() = missions.also { calls += "missions" }
        override suspend fun badges() = badges.also { calls += "badges" }
        override suspend fun achievements() = achievements.also { calls += "achievements" }
        override suspend fun leaderboardFilters() = filters.also { calls += "filters" }
        override suspend fun leaderboard(type: String, period: String) = scores.also { calls += "scores:$type:$period" }
    }

    // ---------- dashboard ----------

    @Test
    fun `dashboard loads every section`() {
        val repo = FakeRepository()

        val state = DashboardViewModel(repo).state.value

        assertEquals(setOf("progression", "streaks", "missions", "badges", "achievements"), repo.calls.toSet())
        assertEquals("Learner", (state.progression as Section.Loaded).data.levelName)
        assertTrue(state.badges is Section.Loaded)
    }

    @Test
    fun `one failing section does not break the others`() {
        val repo = FakeRepository().apply { missions = error }

        val state = DashboardViewModel(repo).state.value

        assertEquals(Section.Failed("Server error. Please try again later."), state.missions)
        assertTrue(state.progression is Section.Loaded)
        assertTrue(state.streaks is Section.Loaded)
        assertTrue(state.achievements is Section.Loaded)
    }

    @Test
    fun `retry reloads only the failed sections`() {
        val repo = FakeRepository().apply { missions = error }
        val vm = DashboardViewModel(repo)
        repo.calls.clear()
        repo.missions = NetworkResult.Success(emptyList())

        vm.retryFailed()

        assertEquals(listOf("missions"), repo.calls)
        assertEquals(Section.Loaded(emptyList<Mission>()), vm.state.value.missions)
    }

    @Test
    fun `a failed refresh keeps the cards on screen and shows a snackbar`() {
        val repo = FakeRepository()
        val vm = DashboardViewModel(repo)
        repo.progression = error

        vm.refresh()

        val state = vm.state.value
        assertEquals(2, (state.progression as Section.Loaded).data.level)
        assertEquals("Server error. Please try again later.", state.transientError)
        assertEquals(false, state.refreshing)

        vm.consumeTransientError()
        assertNull(vm.state.value.transientError)
    }

    @Test
    fun `a successful refresh replaces the data`() {
        val repo = FakeRepository()
        val vm = DashboardViewModel(repo)
        repo.progression = NetworkResult.Success(progression)

        vm.refresh()

        assertEquals(1, (vm.state.value.progression as Section.Loaded).data.level)
    }

    // ---------- leaderboard ----------

    private fun sessionManager(userId: String?): SessionManager = SessionManager(
        dataStore = InMemoryPreferencesDataStore(),
        cipher = FakeTokenCipher(),
        appScope = CoroutineScope(Dispatchers.Unconfined),
        clock = Clock { 1_791_400_000 },
    ).also { manager ->
        if (userId != null) {
            runBlocking {
                manager.save(Session(accessToken = "t", userId = userId, email = "student@example.com", role = "Learner", expiresAtEpochSeconds = 1_791_985_907))
            }
        }
    }

    @Test
    fun `leaderboard opens on Overall, All time`() {
        val repo = FakeRepository()

        val vm = LeaderboardViewModel(repo, SavedStateHandle(), sessionManager(null))

        assertEquals(listOf("filters", "scores:typeOverall:all_time"), repo.calls)
        assertTrue(vm.state.value.entries is Section.Loaded)
    }

    @Test
    fun `switching board and period reloads with the new choice and remembers it`() {
        val repo = FakeRepository()
        val saved = SavedStateHandle()
        val vm = LeaderboardViewModel(repo, saved, sessionManager(null))

        vm.selectType("typeQuiz")
        vm.selectPeriod("weekly")
        vm.selectPeriod("weekly") // same choice: no extra call

        assertEquals(listOf("filters", "scores:typeOverall:all_time", "scores:typeQuiz:all_time", "scores:typeQuiz:weekly"), repo.calls)
        assertEquals("typeQuiz", saved.get<String>("leaderboard_type"))
        assertEquals("weekly", saved.get<String>("leaderboard_period"))
    }

    @Test
    fun `a remembered board the admin switched off falls back to the first one`() {
        val repo = FakeRepository()
        val saved = SavedStateHandle(mapOf("leaderboard_type" to "typeTeam", "leaderboard_period" to "weekly"))

        val vm = LeaderboardViewModel(repo, saved, sessionManager(null))

        assertEquals("typeOverall", vm.state.value.selectedType)
        assertEquals("weekly", vm.state.value.selectedPeriod)
        assertEquals("scores:typeOverall:weekly", repo.calls.last())
    }

    @Test
    fun `the signed-in learner's row is found by id`() {
        val repo = FakeRepository().apply {
            scores = NetworkResult.Success(
                listOf(LeaderboardEntry(1, "u-1", "Asha", 90, 0), LeaderboardEntry(2, "learner-uuid", "Me", 40, 0)),
            )
        }

        val vm = LeaderboardViewModel(repo, SavedStateHandle(), sessionManager("learner-uuid"))

        assertEquals(2, vm.state.value.myEntry?.rank)
    }

    @Test
    fun `settings failure shows an error and retry loads again`() {
        val repo = FakeRepository().apply { filters = error }
        val vm = LeaderboardViewModel(repo, SavedStateHandle(), sessionManager(null))
        assertTrue(vm.state.value.filters is Section.Failed)

        repo.filters = FakeRepository().filters
        vm.retry()

        assertTrue(vm.state.value.filters is Section.Loaded)
        assertEquals("scores:typeOverall:all_time", repo.calls.last())
    }

    // ---------- small helpers ----------

    @Test
    fun `api keys become labels`() {
        assertEquals("Beginner", humanizeKey("xpBeginner"))
        assertEquals("Daily Learning", humanizeKey("streakDailyLearning"))
        assertEquals("Course Completion", humanizeKey("typeCourseCompletion"))
        assertEquals("Expert", humanizeKey("expert"))
        assertEquals("", humanizeKey(""))
    }

    @Test
    fun `goal status from api value or progress`() {
        assertEquals(GoalStatus.Completed, GoalStatus.of("completed"))
        assertEquals(GoalStatus.InProgress, GoalStatus.of("in_progress"))
        assertEquals(GoalStatus.NotStarted, GoalStatus.of("not_started", progress = 0, target = 5))
        assertEquals(GoalStatus.InProgress, GoalStatus.of(null, progress = 2, target = 5))
        assertEquals(GoalStatus.Completed, GoalStatus.of("unknown", progress = 5, target = 5))
    }
}
