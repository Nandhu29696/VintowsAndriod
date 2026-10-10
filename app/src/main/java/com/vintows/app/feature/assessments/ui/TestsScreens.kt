package com.vintows.app.feature.assessments.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.component.DetailTopBar
import com.vintows.app.core.designsystem.component.EmptyState
import com.vintows.app.core.designsystem.component.ErrorState
import com.vintows.app.core.designsystem.component.IconBadge
import com.vintows.app.core.designsystem.component.LoadingState
import com.vintows.app.core.designsystem.component.SectionHeader
import com.vintows.app.core.designsystem.component.TagChip
import com.vintows.app.core.designsystem.component.VCard
import com.vintows.app.core.designsystem.theme.StatusAmber
import com.vintows.app.core.designsystem.theme.StatusBlue
import com.vintows.app.core.designsystem.theme.StatusGrey
import com.vintows.app.core.designsystem.theme.StatusPurple
import com.vintows.app.core.designsystem.theme.StatusRed
import com.vintows.app.core.designsystem.theme.VintowsBlue
import com.vintows.app.core.designsystem.theme.VintowsNavy
import com.vintows.app.core.designsystem.theme.VintowsNavyDark
import com.vintows.app.core.ui.Section
import com.vintows.app.feature.assessments.domain.Assessment

// ---------- Tests tab ----------

@Composable
fun TestsTab(
    onOpenTest: (Assessment) -> Unit,
    viewModel: TestsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TestsContent(state, viewModel::refresh, viewModel::retry, onOpenTest)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestsContent(
    state: TestsUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenTest: (Assessment) -> Unit,
) {
    when (val tests = state.tests) {
        Section.Loading -> LoadingState()
        is Section.Failed -> ErrorState(tests.message, onRetry = onRetry)
        is Section.Loaded -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("Your tests", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            when (tests.data.size) {
                                0 -> "Nothing to take right now"
                                1 -> "1 test available"
                                else -> "${tests.data.size} tests available"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (tests.data.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No tests yet",
                            message = "Tests and assignments from your courses will show up here.",
                            icon = Icons.Outlined.Quiz,
                            modifier = Modifier.padding(top = 48.dp),
                        )
                    }
                } else {
                    items(tests.data, key = { it.id }) { TestCard(it, onClick = { onOpenTest(it) }) }
                }
            }
        }
    }
}

@Composable
fun TestCard(test: Assessment, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = if (test.isAssignment) StatusPurple else StatusBlue
    VCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (test.isAssignment) Icons.Outlined.Assignment else Icons.Outlined.Quiz, tint, size = 48.dp, iconSize = 24.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(test.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                test.nodeName?.let {
                    Text(
                        "${test.levelCode.lowercase().replaceFirstChar(Char::uppercase)} · $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TagChip(test.timeLimitMinutes?.let { "$it min" } ?: "No time limit", StatusGrey)
                    test.maxAttempts?.let { TagChip(if (it == 1) "1 attempt" else "$it attempts", StatusGrey) }
                    if (test.proctored) TagChip("Proctored", StatusRed)
                    if (test.isDraft) TagChip("Draft", StatusAmber)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- intro ----------

@Composable
fun TestIntroRoute(
    onBack: () -> Unit,
    onStart: (TestPlayerDestination) -> Unit,
    viewModel: TestIntroViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Refresh on return from the player: attempts left and "resume" may have changed.
    val firstResume = remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        if (!firstResume.value) viewModel.load()
        firstResume.value = false
        onPauseOrDispose { }
    }
    val assessment = (state.assessment as? Section.Loaded)?.data
    TestIntroScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onStart = { if (assessment != null) onStart(TestPlayerDestination(assessment.id, assessment.name)) },
    )
}

@Composable
fun TestIntroScreen(state: TestIntroUiState, onBack: () -> Unit, onRetry: () -> Unit, onStart: () -> Unit) {
    Scaffold(topBar = { DetailTopBar(title = state.name, subtitle = "Test", onBack = onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val section = state.assessment) {
                Section.Loading -> LoadingState()
                is Section.Failed -> ErrorState(section.message, onRetry = onRetry)
                is Section.Loaded -> IntroBody(section.data, state, onStart)
            }
        }
    }
}

@Composable
private fun IntroBody(test: Assessment, state: TestIntroUiState, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(VintowsNavyDark, VintowsNavy, VintowsBlue)))
                    .padding(20.dp),
            ) {
                TagChip(if (test.isAssignment) "Assignment" else "Test", Color.White)
                Spacer(Modifier.height(10.dp))
                Text("Ready to begin?", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                Text(test.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                test.nodeName?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    HeroStat("Time", test.timeLimitMinutes?.let { "$it min" } ?: "No limit", Modifier.weight(1f))
                    HeroStat("Attempts", attemptsLabel(state), Modifier.weight(1f))
                    HeroStat("Pass mark", test.passMark?.let { formatMark(it) } ?: "—", Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Before you start")
            Spacer(Modifier.height(8.dp))
            VCard(Modifier.fillMaxWidth()) {
                Rule(Icons.Outlined.Timer, if (test.timeLimitMinutes != null) {
                    "The timer starts when you press Start and keeps running if you leave. The test is submitted automatically when time is up."
                } else {
                    "There is no time limit. Take your time, then submit when you're done."
                })
                Rule(Icons.Outlined.FactCheck, "Answers are saved on your phone as you go. You can move between questions and flag ones to review.")
                Rule(Icons.Outlined.WifiOff, "You need an internet connection to start and to submit.")
                if (test.negativeMarking) Rule(Icons.Outlined.Block, "Wrong answers lose marks. Leave a question blank if you're unsure.")
                if (test.proctored) {
                    Rule(Icons.Outlined.Visibility, "This test is proctored. Leaving the app is recorded and reported (limit ${TestPlayerViewModel.MAX_LEAVES}).")
                    Rule(Icons.Outlined.CameraAlt, "Screenshots and screen recording are blocked during the test.")
                }
            }

            if (test.isDraft) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Admin preview: this test is still a draft, so learners can't see it yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            }
        }

        // Bottom action
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            val blocked = (state.check as? Section.Failed)?.message
                ?: (state.check as? Section.Loaded)?.data?.takeIf { !it.canStart }?.let { "You've used all your attempts for this test." }
            if (blocked != null && !state.inProgress) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Icon(Icons.Outlined.Block, contentDescription = null, tint = StatusRed, modifier = Modifier.size(18.dp))
                    Text("  $blocked", style = MaterialTheme.typography.bodyMedium, color = StatusRed)
                }
            }
            Button(
                onClick = onStart,
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                if (state.inProgress) {
                    Icon(Icons.Outlined.Replay, contentDescription = null)
                    Text("  Resume test")
                } else {
                    Text(if (state.check is Section.Loading) "Checking…" else "Start test")
                }
            }
        }
    }
}

private fun attemptsLabel(state: TestIntroUiState): String {
    val check = (state.check as? Section.Loaded)?.data
    val test = (state.assessment as? Section.Loaded)?.data
    return when {
        check?.attemptsRemaining != null -> "${check.attemptsRemaining} left"
        test?.maxAttempts != null -> "${test.maxAttempts}"
        else -> "Unlimited"
    }
}

internal fun formatMark(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun Rule(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        IconBadge(icon, VintowsBlue, size = 32.dp, iconSize = 18.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 6.dp))
    }
}
