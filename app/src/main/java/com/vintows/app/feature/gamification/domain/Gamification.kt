package com.vintows.app.feature.gamification.domain

import com.vintows.app.core.network.NetworkResult

data class Progression(
    val level: Int,
    val levelName: String,
    val totalXp: Long,
    val totalPoints: Long,
    val totalCoins: Long,
    /** XP needed for the next level, or null at the top level. */
    val nextLevelXp: Long?,
    val nextLevelName: String?,
    /** 0f..1f towards the next level. */
    val progress: Float,
)

data class Streak(
    val title: String,
    /** "day" or "week", for "3-day streak" style labels. */
    val unit: String,
    val current: Int,
    val longest: Int,
    /** The next milestone still ahead of [current], if any. */
    val nextMilestone: StreakMilestone?,
)

data class StreakMilestone(val count: Int, val rewardXp: Int, val rewardPoints: Int)

data class Mission(
    val name: String,
    val description: String,
    val icon: String?,
    val period: String?,
    val status: GoalStatus,
    val progress: Int,
    val target: Int,
    val rewardXp: Int,
    val rewardPoints: Int,
    val objectives: List<MissionObjective>,
) {
    val fraction: Float get() = fractionOf(progress, target)
}

data class MissionObjective(val name: String, val progress: Int, val target: Int, val met: Boolean)

data class Badge(
    val key: String,
    val name: String,
    val description: String,
    val icon: String?,
    val earned: Boolean,
    val earnedAt: String?,
    val timesEarned: Int,
    val rewardXp: Int,
    val rewardPoints: Int,
)

data class Achievement(
    val name: String,
    val description: String,
    val status: GoalStatus,
    val progress: Int,
    val target: Int,
    val rewardXp: Int,
    val rewardPoints: Int,
    val completedAt: String?,
) {
    val fraction: Float get() = fractionOf(progress, target)
}

enum class GoalStatus {
    NotStarted, InProgress, Completed;

    companion object {
        /** API values: `not_started`, `in_progress`, `completed`. Anything else is worked out from the progress. */
        fun of(value: String?, progress: Int = 0, target: Int = 0): GoalStatus = when (value?.lowercase()?.replace('-', '_')) {
            "completed", "complete", "achieved" -> Completed
            "in_progress", "active" -> InProgress
            else -> when {
                target in 1..progress -> Completed
                progress > 0 -> InProgress
                else -> NotStarted
            }
        }
    }
}

/** A board (Overall, Quiz…) or a period (All time, Daily…) the learner can pick. */
data class LeaderboardOption(val key: String, val label: String)

data class LeaderboardFilters(val types: List<LeaderboardOption>, val periods: List<LeaderboardOption>)

data class LeaderboardEntry(
    val rank: Int,
    val learnerId: String?,
    val name: String,
    val points: Long,
    val xp: Long,
)

interface GamificationRepository {
    suspend fun progression(): NetworkResult<Progression>
    suspend fun streaks(): NetworkResult<List<Streak>>
    suspend fun missions(): NetworkResult<List<Mission>>
    suspend fun badges(): NetworkResult<List<Badge>>
    suspend fun achievements(): NetworkResult<List<Achievement>>
    suspend fun leaderboardFilters(): NetworkResult<LeaderboardFilters>
    suspend fun leaderboard(type: String, period: String): NetworkResult<List<LeaderboardEntry>>
}

internal fun fractionOf(progress: Int, target: Int): Float =
    if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
