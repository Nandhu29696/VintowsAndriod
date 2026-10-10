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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.component.DetailTopBar
import com.vintows.app.core.designsystem.component.EmptyState
import com.vintows.app.core.designsystem.component.VCard
import com.vintows.app.core.designsystem.component.ErrorState
import com.vintows.app.core.designsystem.component.InitialsAvatar
import com.vintows.app.core.designsystem.component.LoadingState
import com.vintows.app.core.designsystem.theme.StatusGrey
import com.vintows.app.core.ui.Section
import com.vintows.app.feature.gamification.domain.LeaderboardEntry
import com.vintows.app.feature.gamification.domain.LeaderboardOption

@Composable
fun LeaderboardRoute(
    onBack: () -> Unit,
    viewModel: LeaderboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LeaderboardScreen(
        state = state,
        onBack = onBack,
        onSelectType = viewModel::selectType,
        onSelectPeriod = viewModel::selectPeriod,
        onRetry = viewModel::retry,
    )
}

@Composable
fun LeaderboardScreen(
    state: LeaderboardUiState,
    onBack: () -> Unit,
    onSelectType: (String) -> Unit,
    onSelectPeriod: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            DetailTopBar(title = "Leaderboard", subtitle = "Learners ranked by points", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val filters = state.filters) {
                Section.Loading -> LoadingState()
                is Section.Failed -> ErrorState(filters.message, onRetry = onRetry)
                is Section.Loaded -> {
                    OptionRow(filters.data.types, state.selectedType, onSelectType, Modifier.padding(top = 8.dp))
                    OptionRow(filters.data.periods, state.selectedPeriod, onSelectPeriod)
                    HorizontalDivider(Modifier.padding(top = 4.dp))
                    Box(Modifier.weight(1f)) { Entries(state, onRetry) }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    options: List<LeaderboardOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.key }) { option ->
            FilterChip(
                selected = option.key == selected,
                onClick = { onSelect(option.key) },
                label = { Text(option.label) },
            )
        }
    }
}

@Composable
private fun Entries(state: LeaderboardUiState, onRetry: () -> Unit) {
    when (val entries = state.entries) {
        Section.Loading -> LoadingState()
        is Section.Failed -> ErrorState(entries.message, onRetry = onRetry)
        is Section.Loaded -> if (entries.data.isEmpty()) {
            EmptyState(
                title = "Nobody has scored yet",
                message = "Complete lessons and quizzes to earn points and be the first on this board.",
                icon = Icons.Outlined.EmojiEvents,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.myEntry?.let { mine ->
                    item(key = "me") { EntryRow(mine, isMe = true, label = "Your rank") }
                }
                items(entries.data, key = { "${it.rank}-${it.learnerId ?: it.name}" }) { entry ->
                    EntryRow(entry, isMe = entry.learnerId != null && entry.learnerId == state.myId)
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LeaderboardEntry, isMe: Boolean, label: String? = null) {
    VCard(
        modifier = Modifier.fillMaxWidth(),
        color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RankBadge(entry.rank)
            Spacer(Modifier.width(12.dp))
            InitialsAvatar(entry.name, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (label != null) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (isMe && label == null) "${entry.name} (you)" else entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.xp > 0) {
                    Text("${entry.xp.grouped()} XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(entry.points.grouped(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("points", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Gold, silver and bronze for the top three. */
@Composable
private fun RankBadge(rank: Int) {
    val color = when (rank) {
        1 -> Color(0xFFD4A017)
        2 -> Color(0xFF9EA7B3)
        3 -> Color(0xFFB8733D)
        else -> null
    }
    Box(
        Modifier.size(32.dp).background((color ?: StatusGrey).copy(alpha = if (color != null) 1f else 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (color != null) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}
