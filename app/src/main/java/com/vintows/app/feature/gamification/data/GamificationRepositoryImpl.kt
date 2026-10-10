package com.vintows.app.feature.gamification.data

import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.network.map
import com.vintows.app.feature.gamification.domain.Achievement
import com.vintows.app.feature.gamification.domain.Badge
import com.vintows.app.feature.gamification.domain.GamificationRepository
import com.vintows.app.feature.gamification.domain.LeaderboardEntry
import com.vintows.app.feature.gamification.domain.LeaderboardFilters
import com.vintows.app.feature.gamification.domain.Mission
import com.vintows.app.feature.gamification.domain.Progression
import com.vintows.app.feature.gamification.domain.Streak
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamificationRepositoryImpl @Inject constructor(
    private val api: GamificationApi,
    private val apiCaller: ApiCaller,
) : GamificationRepository {

    override suspend fun progression(): NetworkResult<Progression> =
        apiCaller.call { api.progression() }.map { it.toDomain() }

    override suspend fun streaks(): NetworkResult<List<Streak>> =
        apiCaller.call { api.streaks() }.map { list -> list.map { it.toDomain() } }

    override suspend fun missions(): NetworkResult<List<Mission>> =
        apiCaller.call { api.missions() }.map { list -> list.map { it.toDomain() } }

    /** Earned badges first, so the learner's progress is visible without scrolling. */
    override suspend fun badges(): NetworkResult<List<Badge>> =
        apiCaller.call { api.badges() }.map { list -> list.map { it.toDomain() }.sortedByDescending { it.earned } }

    override suspend fun achievements(): NetworkResult<List<Achievement>> =
        apiCaller.call { api.achievements() }.map { list -> list.map { it.toDomain() } }

    override suspend fun leaderboardFilters(): NetworkResult<LeaderboardFilters> =
        apiCaller.call { api.leaderboardSettings() }.map { it.toFilters() }

    override suspend fun leaderboard(type: String, period: String): NetworkResult<List<LeaderboardEntry>> =
        apiCaller.call { api.leaderboardScores(leaderboardType = type, periodType = period) }
            .map { rows -> rows.mapIndexed { index, row -> row.toLeaderboardEntry(position = index + 1) }.sortedBy { it.rank } }
}
