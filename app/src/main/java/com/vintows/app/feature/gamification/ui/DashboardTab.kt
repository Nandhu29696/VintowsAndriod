package com.vintows.app.feature.gamification.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.component.TagChip
import com.vintows.app.core.designsystem.theme.StatusAmber
import com.vintows.app.core.designsystem.theme.StatusBlue
import com.vintows.app.core.designsystem.theme.StatusGreen
import com.vintows.app.core.designsystem.theme.StatusGrey
import com.vintows.app.core.designsystem.theme.VintowsAccent
import com.vintows.app.core.designsystem.theme.VintowsBlue
import com.vintows.app.core.designsystem.theme.VintowsNavy
import com.vintows.app.core.ui.Section
import com.vintows.app.core.util.DateFormatter
import com.vintows.app.feature.gamification.domain.Achievement
import com.vintows.app.feature.gamification.domain.Badge
import com.vintows.app.feature.gamification.domain.GoalStatus
import com.vintows.app.feature.gamification.domain.Mission
import com.vintows.app.feature.gamification.domain.Progression
import com.vintows.app.feature.gamification.domain.Streak
import java.text.NumberFormat

/** Learner Home tab: level, streaks, missions, badges and achievements. */
@Composable
fun DashboardTab(
    snackbar: SnackbarHostState,
    onOpenLeaderboard: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.transientError) {
        state.transientError?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeTransientError()
        }
    }
    DashboardContent(
        state = state,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retryFailed,
        onOpenLeaderboard = onOpenLeaderboard,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenLeaderboard: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            section(state.progression, title = null, onRetry = onRetry) { item { LevelCard(it) } }
            item { LeaderboardLink(onOpenLeaderboard) }

            section(state.streaks, title = "Streaks", onRetry = onRetry) { streaks ->
                if (streaks.isEmpty()) {
                    item { Muted("Streaks aren't set up yet.") }
                } else {
                    streaks.chunked(2).forEach { pair ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { StreakCard(it, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            section(state.missions, title = "Missions", onRetry = onRetry) { missions ->
                if (missions.isEmpty()) {
                    item { Muted("No missions right now. Check back soon.") }
                } else {
                    items(missions, key = { "mission-${it.name}" }) { MissionCard(it) }
                }
            }

            section(
                state.badges,
                title = "Badges",
                trailing = { badges -> "${badges.count { it.earned }} of ${badges.size} earned" },
                onRetry = onRetry,
            ) { badges ->
                if (badges.isEmpty()) {
                    item { Muted("No badges available yet.") }
                } else {
                    badges.chunked(2).forEach { pair ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { BadgeTile(it, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            section(
                state.achievements,
                title = "Achievements",
                trailing = { list -> "${list.count { it.status == GoalStatus.Completed }} of ${list.size} done" },
                onRetry = onRetry,
            ) { achievements ->
                if (achievements.isEmpty()) {
                    item { Muted("No achievements available yet.") }
                } else {
                    items(achievements, key = { "achievement-${it.name}" }) { AchievementRow(it) }
                }
            }
        }
    }
}

/** Header + the section's own loading, error or content items. */
private fun <T> LazyListScope.section(
    section: Section<T>,
    title: String?,
    onRetry: () -> Unit,
    trailing: ((T) -> String)? = null,
    content: LazyListScope.(T) -> Unit,
) {
    if (title != null) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (section is Section.Loaded && trailing != null) {
                    Text(trailing(section.data), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    when (section) {
        Section.Loading -> item {
            Box(Modifier.fillMaxWidth().height(if (title == null) 160.dp else 72.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }
        is Section.Failed -> item { SectionError(section.message, onRetry) }
        is Section.Loaded -> content(section.data)
    }
}

@Composable
private fun SectionError(message: String, onRetry: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Couldn't load. $message",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LevelCard(p: Progression) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(VintowsNavy, VintowsBlue)))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
                    CircularProgressIndicator(
                        progress = { p.progress },
                        modifier = Modifier.fillMaxSize(),
                        color = VintowsAccent,
                        trackColor = Color.White.copy(alpha = 0.18f),
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LEVEL", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text("${p.level}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.levelName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    val toNext = p.nextLevelXp?.let { (it - p.totalXp).coerceAtLeast(0) }
                    Text(
                        when {
                            toNext == null -> "Top level reached"
                            p.nextLevelName != null -> "${toNext.grouped()} XP to ${p.nextLevelName}"
                            else -> "${toNext.grouped()} XP to the next level"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Text(
                        "${(p.progress * 100).toInt()}% complete",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("XP", p.totalXp, Modifier.weight(1f))
                Stat("Points", p.totalPoints, Modifier.weight(1f))
                Stat("Coins", p.totalCoins, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Long, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.grouped(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun LeaderboardLink(onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Leaderboard, contentDescription = null, tint = VintowsBlue)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Leaderboard", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("See where you rank", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun StreakCard(streak: Streak, modifier: Modifier) {
    val active = streak.current > 0
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (active) VintowsAccent else StatusGrey,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(plural(streak.current, streak.unit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(streak.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Best: ${plural(streak.longest, streak.unit)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            streak.nextMilestone?.let { m ->
                Text(
                    "Next: ${plural(m.count, streak.unit)}" + rewardText(m.rewardXp, m.rewardPoints).let { if (it.isEmpty()) "" else " · $it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MissionCard(mission: Mission) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(bootstrapIcon(mission.icon), contentDescription = null, tint = VintowsBlue, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(mission.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                mission.period?.let { TagChip(it, StatusBlue) }
            }
            if (mission.description.isNotBlank()) {
                Text(
                    mission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            mission.objectives.forEach { objective ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (objective.met) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (objective.met) "Done" else "Not done",
                        tint = if (objective.met) StatusGreen else StatusGrey,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(objective.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${objective.progress}/${objective.target}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            GoalProgress(mission.fraction, mission.status, "${mission.progress}/${mission.target}", rewardText(mission.rewardXp, mission.rewardPoints))
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, modifier: Modifier) {
    val tint = if (badge.earned) StatusAmber else StatusGrey
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(52.dp).background(tint.copy(alpha = if (badge.earned) 0.16f else 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(bootstrapIcon(badge.icon), contentDescription = null, tint = tint.copy(alpha = if (badge.earned) 1f else 0.6f))
            }
            Spacer(Modifier.height(8.dp))
            Text(badge.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                badge.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            val footer = when {
                badge.earned && badge.timesEarned > 1 -> "Earned ×${badge.timesEarned}"
                badge.earned -> DateFormatter.date(badge.earnedAt).let { if (it.isEmpty()) "Earned" else "Earned $it" }
                else -> rewardText(badge.rewardXp, badge.rewardPoints).ifEmpty { "Locked" }
            }
            TagChip(footer, if (badge.earned) StatusGreen else StatusGrey)
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(achievement.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (achievement.description.isNotBlank()) {
                Text(achievement.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GoalProgress(
                achievement.fraction,
                achievement.status,
                "${achievement.progress.toLong().grouped()} / ${achievement.target.toLong().grouped()}",
                rewardText(achievement.rewardXp, achievement.rewardPoints),
            )
        }
    }
}

@Composable
private fun GoalProgress(fraction: Float, status: GoalStatus, count: String, reward: String) {
    val color = when (status) {
        GoalStatus.Completed -> StatusGreen
        GoalStatus.InProgress -> VintowsBlue
        GoalStatus.NotStarted -> StatusGrey
    }
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
    )
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (status == GoalStatus.Completed) "Completed" else count,
            style = MaterialTheme.typography.labelMedium,
            color = if (status == GoalStatus.Completed) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (reward.isNotEmpty()) Text(reward, style = MaterialTheme.typography.labelMedium, color = VintowsAccent)
    }
}

internal fun rewardText(xp: Int, points: Int): String = listOfNotNull(
    xp.takeIf { it > 0 }?.let { "+$it XP" },
    points.takeIf { it > 0 }?.let { "+$it pts" },
).joinToString(" · ")

internal fun plural(count: Int, unit: String): String = "$count $unit${if (count == 1) "" else "s"}"

internal fun Long.grouped(): String = NumberFormat.getIntegerInstance().format(this)
