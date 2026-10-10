package com.vintows.app.feature.gamification.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Shapes copied from the QA responses (2026-10-09). Every field has a default so a missing key never fails a section.

@Serializable
data class ProgressionDto(
    val totalPoints: Long = 0,
    val totalXp: Long = 0,
    val totalCoins: Long = 0,
    val level: Int = 1,
    val levelName: String = "",
    val nextLevelXp: Long? = null,
    val progressPercent: Double = 0.0,
    val levels: List<LevelDto> = emptyList(),
)

@Serializable
data class LevelDto(val key: String = "", val xp: Long = 0)

@Serializable
data class StreakDto(
    val key: String = "",
    val periodType: String = "daily",
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastActivityAt: String? = null,
    val milestones: List<StreakMilestoneDto> = emptyList(),
)

@Serializable
data class StreakMilestoneDto(
    val days: Int = 0,
    val rewardXp: Int = 0,
    val rewardPoints: Int = 0,
)

@Serializable
data class MissionDto(
    val code: String = "",
    val name: String = "",
    val description: String = "",
    val icon: String? = null,
    val periodType: String? = null,
    val rewardPoints: Int = 0,
    val rewardXp: Int = 0,
    val rewardCoins: Int = 0,
    val status: String = "not_started",
    val progress: Int = 0,
    val target: Int = 0,
    val objectives: List<MissionObjectiveDto> = emptyList(),
)

@Serializable
data class MissionObjectiveDto(
    val name: String = "",
    val target: Int = 0,
    val progress: Int = 0,
    val met: Boolean = false,
)

@Serializable
data class BadgeDto(
    val key: String = "",
    val name: String = "",
    val description: String = "",
    /** Bootstrap icon name, e.g. `bi-trophy`. */
    val icon: String? = null,
    val rewardPoints: Int = 0,
    val rewardXp: Int = 0,
    val earned: Boolean = false,
    val earnedAt: String? = null,
    val timesEarned: Int = 0,
)

@Serializable
data class AchievementDto(
    val key: String = "",
    val name: String = "",
    val description: String = "",
    val rewardPoints: Int = 0,
    val rewardXp: Int = 0,
    val status: String = "not_started",
    val progress: Int = 0,
    val target: Int = 0,
    val completedAt: String? = null,
)

@Serializable
data class LeaderboardSettingDto(
    @SerialName("setting_key") val key: String = "",
    @SerialName("setting_name") val name: String = "",
    /** Switched on by the admin (the web shows only these). */
    @SerialName("setting_value") val value: Boolean = false,
    @SerialName("is_enabled") val enabled: Boolean = false,
    @SerialName("sequence_number") val sequence: Int = 0,
)
