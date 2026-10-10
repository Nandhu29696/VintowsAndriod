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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.component.IconBadge
import com.vintows.app.core.designsystem.component.SectionHeader
import com.vintows.app.core.designsystem.component.TagChip
import com.vintows.app.core.designsystem.component.VCard
import com.vintows.app.core.designsystem.icon.bootstrapIcon
import com.vintows.app.core.designsystem.theme.StatusAmber
import com.vintows.app.core.designsystem.theme.StatusBlue
import com.vintows.app.core.designsystem.theme.StatusGreen
import com.vintows.app.core.designsystem.theme.StatusGrey
import com.vintows.app.core.designsystem.theme.StatusPurple
import com.vintows.app.core.designsystem.theme.VintowsAccent
import com.vintows.app.core.designsystem.theme.VintowsBlue
import com.vintows.app.core.designsystem.theme.VintowsNavy
import com.vintows.app.core.designsystem.theme.VintowsNavyDark
import com.vintows.app.core.ui.Section
import com.vintows.app.core.util.DateFormatter
import com.vintows.app.feature.gamification.domain.Achievement
import com.vintows.app.feature.gamification.domain.Badge
import com.vintows.app.feature.gamification.domain.GoalStatus
import com.vintows.app.feature.gamification.domain.Mission
import com.vintows.app.feature.gamification.domain.Progression
import com.vintows.app.feature.gamification.domain.Streak
import java.text.NumberFormat
import java.time.LocalTime

/** Learner Home tab: greeting, level, streaks, missions, badges and achievements. */
@Composable
fun DashboardTab(
    displayName: String,
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
        displayName = displayName,
        state = state,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retryFailed,
        onOpenLeaderboard = onOpenLeaderboard,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    displayName: String,
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenLeaderboard: () -> Unit,
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Greeting(displayName) }
            section(state.progression, title = null, onRetry = onRetry) { item { LevelCard(it) } }
            item { LeaderboardLink(onOpenLeaderboard) }

            section(state.streaks, title = "Streaks", onRetry = onRetry) { streaks ->
                if (streaks.isEmpty()) {
                    item { Muted("Streaks aren't set up yet.") }
                } else {
                    pairs(streaks) { streak, modifier -> StreakCard(streak, modifier) }
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
                    pairs(badges) { badge, modifier -> BadgeTile(badge, modifier) }
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

/** Two-column rows of equal-width cards. */
private fun <T> LazyListScope.pairs(list: List<T>, card: @Composable (T, Modifier) -> Unit) {
    list.chunked(2).forEach { pair ->
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { card(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
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
            SectionHeader(
                title = title,
                trailing = (section as? Section.Loaded)?.let { loaded -> trailing?.invoke(loaded.data) },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    when (section) {
        Section.Loading -> item {
            Box(Modifier.fillMaxWidth().height(if (title == null) 180.dp else 72.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }
        is Section.Failed -> item { SectionError(section.message, onRetry) }
        is Section.Loaded -> content(section.data)
    }
}

@Composable
private fun Greeting(name: String) {
    val hour = LocalTime.now().hour
    val salutation = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(salutation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (name.isBlank()) "Ready to learn?" else "Hi, $name",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionError(message: String, onRetry: () -> Unit) {
    VCard(Modifier.fillMaxWidth()) {
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
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(VintowsNavyDark, VintowsNavy, VintowsBlue))),
    ) {
        // Soft decorative circles behind the content.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-70).dp)
                .size(180.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 50.dp)
                .size(120.dp)
                .background(Color.White.copy(alpha = 0.04f), CircleShape),
        )
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                    CircularProgressIndicator(
                        progress = { p.progress },
                        modifier = Modifier.fillMaxSize(),
                        color = VintowsAccent,
                        trackColor = Color.White.copy(alpha = 0.16f),
                        strokeWidth = 9.dp,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LEVEL", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text("${p.level}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    TagChip("${(p.progress * 100).toInt()}% to next level", Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(p.levelName, style = MaterialTheme.typography.headlineSmall, color = Color.White)
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
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    .padding(vertical = 12.dp),
            ) {
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
        Text(value.grouped(), style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun LeaderboardLink(onClick: () -> Unit) {
    VCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Leaderboard, StatusPurple, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Leaderboard", style = MaterialTheme.typography.titleSmall)
                Text("See where you rank among learners", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StreakCard(streak: Streak, modifier: Modifier) {
    val active = streak.current > 0
    VCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            IconBadge(Icons.Outlined.LocalFireDepartment, if (active) VintowsAccent else StatusAmber, size = 40.dp)
            Spacer(Modifier.height(10.dp))
            Text(plural(streak.current, streak.unit), style = MaterialTheme.typography.titleLarge)
            Text(streak.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(
                "Best ${plural(streak.longest, streak.unit)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            streak.nextMilestone?.let { m ->
                val reward = rewardText(m.rewardXp, m.rewardPoints)
                Text(
                    "Next ${plural(m.count, streak.unit)}" + if (reward.isEmpty()) "" else " · $reward",
                    style = MaterialTheme.typography.labelSmall,
                    color = VintowsAccent,
                )
            }
        }
    }
}

@Composable
private fun MissionCard(mission: Mission) {
    VCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(bootstrapIcon(mission.icon, Icons.Outlined.Flag), VintowsBlue)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(mission.name, style = MaterialTheme.typography.titleSmall)
                    if (mission.description.isNotBlank()) {
                        Text(mission.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                mission.period?.let { TagChip(it, StatusBlue) }
            }
            Spacer(Modifier.height(6.dp))
            mission.objectives.forEach { objective ->
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (objective.met) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (objective.met) "Done" else "Not done",
                        tint = if (objective.met) StatusGreen else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(objective.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${objective.progress}/${objective.target}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            GoalProgress(mission.fraction, mission.status, "${mission.progress} of ${mission.target} done", rewardText(mission.rewardXp, mission.rewardPoints))
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, modifier: Modifier) {
    val tint = if (badge.earned) StatusAmber else StatusGrey
    VCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(
                        if (badge.earned) {
                            Brush.linearGradient(listOf(Color(0xFFFFD36B), StatusAmber))
                        } else {
                            Brush.linearGradient(listOf(tint.copy(alpha = 0.10f), tint.copy(alpha = 0.10f)))
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    bootstrapIcon(badge.icon, Icons.Outlined.WorkspacePremium),
                    contentDescription = null,
                    tint = if (badge.earned) Color.White else tint.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(badge.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                badge.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
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
    val tint = when (achievement.status) {
        GoalStatus.Completed -> StatusGreen
        GoalStatus.InProgress -> VintowsBlue
        GoalStatus.NotStarted -> StatusGrey
    }
    VCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            IconBadge(Icons.Outlined.EmojiEvents, tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(achievement.name, style = MaterialTheme.typography.titleSmall)
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
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
