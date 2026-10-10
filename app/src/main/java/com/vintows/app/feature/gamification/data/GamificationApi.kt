package com.vintows.app.feature.gamification.data

import com.vintows.app.core.network.ApiEnvelope
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/** Learner gamification endpoints. All read-only; the backend works out the learner from the JWT. */
interface GamificationApi {
    @GET("gamification/progression")
    suspend fun progression(): ApiEnvelope<ProgressionDto>

    @GET("gamification/streak")
    suspend fun streaks(): ApiEnvelope<List<StreakDto>>

    @GET("gamification/missions")
    suspend fun missions(): ApiEnvelope<List<MissionDto>>

    @GET("gamification/badges")
    suspend fun badges(): ApiEnvelope<List<BadgeDto>>

    @GET("gamification/achievements")
    suspend fun achievements(): ApiEnvelope<List<AchievementDto>>

    /** Board types (`typeOverall`, `typeQuiz`…) and periods (`typeDaily`…) share this list. */
    @GET("gamification/leaderboard/settings")
    suspend fun leaderboardSettings(@Query("category") category: String = "types"): ApiEnvelope<List<LeaderboardSettingDto>>

    /**
     * Rows are kept as raw JSON: every QA board is still empty, so the row shape hasn't been observed.
     * [toLeaderboardEntry] reads the `leaderboard_scores` column names and their camelCase forms.
     */
    @GET("gamification/leaderboard/scores")
    suspend fun leaderboardScores(
        @Query("leaderboardType") leaderboardType: String,
        @Query("periodType") periodType: String,
        @Query("limit") limit: Int = LEADERBOARD_LIMIT,
    ): ApiEnvelope<List<JsonObject>>

    companion object {
        const val LEADERBOARD_LIMIT = 100
    }
}
