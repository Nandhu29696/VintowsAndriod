package com.vintows.app.feature.courses.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.component.EmptyState
import com.vintows.app.core.designsystem.component.ErrorState
import com.vintows.app.core.designsystem.component.LoadingState
import com.vintows.app.core.designsystem.component.PriceTag
import com.vintows.app.core.designsystem.component.SearchField
import com.vintows.app.core.designsystem.component.VCard
import com.vintows.app.core.designsystem.theme.coverGradientFor
import com.vintows.app.core.ui.Section
import com.vintows.app.feature.courses.domain.CourseNode

/** Learner Courses tab: every program, searchable, as cover cards. */
@Composable
fun CoursesTab(
    onOpenProgram: (CourseNode) -> Unit,
    viewModel: CoursesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CoursesContent(state, viewModel::onQueryChange, viewModel::refresh, viewModel::retry, onOpenProgram)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesContent(
    state: CoursesUiState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenProgram: (CourseNode) -> Unit,
) {
    when (val programs = state.programs) {
        Section.Loading -> LoadingState()
        is Section.Failed -> ErrorState(programs.message, onRetry = onRetry)
        is Section.Loaded -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            val visible = state.visiblePrograms
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("Explore courses", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            when (programs.data.size) {
                                0 -> "No programs yet"
                                1 -> "1 program to learn from"
                                else -> "${programs.data.size} programs to learn from"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (programs.data.isNotEmpty()) {
                    item { SearchField(state.query, onQueryChange, placeholder = "Search programs") }
                }
                when {
                    programs.data.isEmpty() -> item {
                        EmptyState(
                            title = "No courses yet",
                            message = "Programs your institution publishes will show up here.",
                            icon = Icons.Outlined.School,
                            modifier = Modifier.padding(top = 48.dp),
                        )
                    }
                    visible.isEmpty() -> item {
                        EmptyState(
                            title = "No matches",
                            message = "Nothing matches \"${state.query.trim()}\".",
                            icon = Icons.Outlined.SearchOff,
                            modifier = Modifier.padding(top = 32.dp),
                        )
                    }
                    else -> items(visible, key = { it.id }) { ProgramCard(it, onClick = { onOpenProgram(it) }) }
                }
            }
        }
    }
}

@Composable
private fun ProgramCard(program: CourseNode, onClick: () -> Unit) {
    val (start, end) = coverGradientFor(program.id)
    VCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Brush.linearGradient(listOf(start, end))),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-30).dp)
                    .size(140.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape),
            )
            Text(
                program.name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp),
            )
            PriceTag(program.isPaid, program.price, Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.White, MaterialTheme.shapes.extraSmall))
        }
        Column(Modifier.padding(16.dp)) {
            Text(program.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                program.description ?: "Subjects, lessons and practice material.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Start learning", style = MaterialTheme.typography.labelLarge, color = start)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = start, modifier = Modifier.size(18.dp))
            }
        }
    }
}
