package com.vintows.app.feature.gamification.data

import com.vintows.app.feature.gamification.domain.Achievement
import com.vintows.app.feature.gamification.domain.Badge
import com.vintows.app.feature.gamification.domain.GoalStatus
import com.vintows.app.feature.gamification.domain.LeaderboardEntry
import com.vintows.app.feature.gamification.domain.LeaderboardFilters
import com.vintows.app.feature.gamification.domain.LeaderboardOption
import com.vintows.app.feature.gamification.domain.Mission
import com.vintows.app.feature.gamification.domain.MissionObjective
import com.vintows.app.feature.gamification.domain.Progression
import com.vintows.app.feature.gamification.domain.Streak
import com.vintows.app.feature.gamification.domain.StreakMilestone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Turns an API key into a label: `xpBeginner` → "Beginner", `streakDailyLearning` → "Daily Learning".
 * The lowercase prefix (`xp`, `streak`, `type`) is a namespace, not part of the name.
 */
internal fun humanizeKey(key: String): String {
    if (key.isBlank()) return ""
    val firstUpper = key.indexOfFirst { it.isUpperCase() }
    val name = if (firstUpper > 0) key.substring(firstUpper) else key
    return name
        .replace('_', ' ')
        .replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}

internal fun ProgressionDto.toDomain(): Progression {
    val sorted = levels.sortedBy { it.xp }
    val next = sorted.firstOrNull { it.xp > totalXp }
    return Progression(
        level = level,
        levelName = humanizeKey(levelName).ifBlank { "Level $level" },
        totalXp = totalXp,
        totalPoints = totalPoints,
        totalCoins = totalCoins,
        nextLevelXp = nextLevelXp ?: next?.xp,
        nextLevelName = next?.let { humanizeKey(it.key) },
        progress = (progressPercent / 100.0).toFloat().coerceIn(0f, 1f),
    )
}

internal fun StreakDto.toDomain(): Streak = Streak(
    title = humanizeKey(key).ifBlank { "Streak" },
    unit = if (periodType.equals("weekly", ignoreCase = true)) "week" else "day",
    current = currentStreak,
    longest = longestStreak,
    nextMilestone = milestones
        .sortedBy { it.days }
        .firstOrNull { it.days > currentStreak }
        ?.let { StreakMilestone(count = it.days, rewardXp = it.rewardXp, rewardPoints = it.rewardPoints) },
)

internal fun MissionDto.toDomain(): Mission = Mission(
    name = name.ifBlank { humanizeKey(code) },
    description = description,
    icon = icon,
    period = periodType?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercaseChar() },
    status = GoalStatus.of(status, progress, target),
    progress = progress,
    target = target,
    rewardXp = rewardXp,
    rewardPoints = rewardPoints,
    objectives = objectives.map { MissionObjective(it.name, it.progress, it.target, it.met) },
)

internal fun BadgeDto.toDomain(): Badge = Badge(
    key = key,
    name = name.ifBlank { humanizeKey(key) },
    description = description,
    icon = icon,
    earned = earned,
    earnedAt = earnedAt,
    timesEarned = timesEarned,
    rewardXp = rewardXp,
    rewardPoints = rewardPoints,
)

internal fun AchievementDto.toDomain(): Achievement = Achievement(
    name = name.ifBlank { humanizeKey(key) },
    description = description,
    status = GoalStatus.of(status, progress, target),
    progress = progress,
    target = target,
    rewardXp = rewardXp,
    rewardPoints = rewardPoints,
    completedAt = completedAt,
)

/** `typeDaily` … `typeYearly` are periods; the API's `periodType` value is the lowercase word. */
private val PERIOD_KEYS = mapOf(
    "typeDaily" to "daily",
    "typeWeekly" to "weekly",
    "typeMonthly" to "monthly",
    "typeQuarterly" to "quarterly",
    "typeYearly" to "yearly",
)

const val ALL_TIME_PERIOD = "all_time"
const val OVERALL_BOARD = "typeOverall"

/**
 * Splits the settings list into boards and periods, keeping only what the admin switched on, in the admin's order.
 * "All time" is always offered first because the web opens the leaderboard on it.
 */
internal fun List<LeaderboardSettingDto>.toFilters(): LeaderboardFilters {
    val active = filter { it.enabled && it.value && it.key.isNotBlank() }.sortedBy { it.sequence }
    val types = active
        .filter { it.key !in PERIOD_KEYS }
        .map { LeaderboardOption(it.key, it.name.ifBlank { humanizeKey(it.key) }) }
        .ifEmpty { listOf(LeaderboardOption(OVERALL_BOARD, "Overall")) }
    val periods = listOf(LeaderboardOption(ALL_TIME_PERIOD, "All time")) +
        active.mapNotNull { setting ->
            PERIOD_KEYS[setting.key]?.let { LeaderboardOption(it, setting.name.ifBlank { humanizeKey(setting.key) }) }
        }
    return LeaderboardFilters(types, periods)
}

/**
 * Tolerant row mapping. `leaderboard_scores` has learner_id, points and xp; the name may arrive as a joined
 * column or nested learner object, so several spellings are tried. Rows without a rank are ranked by position.
 */
internal fun JsonObject.toLeaderboardEntry(position: Int): LeaderboardEntry {
    val nested = (this["learner"] as? JsonObject) ?: (this["user"] as? JsonObject)
    fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        ((this[key] ?: nested?.get(key)) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    }
    fun num(vararg keys: String): Double? = keys.firstNotNullOfOrNull { key ->
        ((this[key] ?: nested?.get(key)) as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
    }
    return LeaderboardEntry(
        rank = num("rank", "position")?.toInt()?.takeIf { it > 0 } ?: position,
        learnerId = str("learnerId", "learner_id", "userId", "user_id", "id"),
        name = str("fullName", "full_name", "learnerName", "learner_name", "name", "email") ?: "Learner",
        points = num("points", "totalPoints", "total_points", "score")?.toLong() ?: 0,
        xp = num("xp", "totalXp", "total_xp")?.toLong() ?: 0,
    )
}
