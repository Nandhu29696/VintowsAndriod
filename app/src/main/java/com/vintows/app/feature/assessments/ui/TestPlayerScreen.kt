package com.vintows.app.feature.assessments.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vintows.app.core.designsystem.component.DetailTopBar
import com.vintows.app.core.designsystem.component.ErrorState
import com.vintows.app.core.designsystem.component.LoadingState
import com.vintows.app.core.designsystem.component.TagChip
import com.vintows.app.core.designsystem.component.VCard
import com.vintows.app.core.designsystem.theme.StatusAmber
import com.vintows.app.core.designsystem.theme.StatusGreen
import com.vintows.app.core.designsystem.theme.StatusRed
import com.vintows.app.core.designsystem.theme.VintowsAccent
import com.vintows.app.core.designsystem.theme.VintowsBlue
import com.vintows.app.core.designsystem.theme.VintowsNavy
import com.vintows.app.core.designsystem.theme.VintowsNavyDark
import com.vintows.app.feature.assessments.domain.AnswerValue
import com.vintows.app.feature.assessments.domain.Question
import com.vintows.app.feature.assessments.domain.QuestionKind

@Composable
fun TestPlayerRoute(
    onExit: () -> Unit,
    viewModel: TestPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureWindow()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAppBackgrounded() }
    TestPlayerScreen(
        state = state,
        onExit = onExit,
        onRetry = viewModel::retry,
        onGoTo = viewModel::goTo,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onSelectOption = viewModel::selectOption,
        onText = viewModel::setText,
        onBlank = viewModel::setBlank,
        onToggleFlag = viewModel::toggleFlag,
        onSubmit = viewModel::submit,
        onDismissLeaveWarning = viewModel::dismissLeaveWarning,
        onTransientErrorShown = viewModel::consumeTransientError,
    )
}

/** Blocks screenshots and screen recording while the test is open (FLAG_SECURE). */
@Composable
private fun SecureWindow() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestPlayerScreen(
    state: TestPlayerUiState,
    onExit: () -> Unit,
    onRetry: () -> Unit,
    onGoTo: (Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSelectOption: (Question, Int) -> Unit,
    onText: (Question, String) -> Unit,
    onBlank: (Question, Int, String) -> Unit,
    onToggleFlag: (Question) -> Unit,
    onSubmit: () -> Unit,
    onDismissLeaveWarning: () -> Unit,
    onTransientErrorShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var showPalette by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val running = state.phase == PlayerPhase.Running

    LaunchedEffect(state.transientError) {
        state.transientError?.let {
            snackbar.showSnackbar(it)
            onTransientErrorShown()
        }
    }
    BackHandler(enabled = running || state.phase == PlayerPhase.Submitting) { if (running) confirmLeave = true }

    Scaffold(
        topBar = {
            DetailTopBar(
                title = state.name,
                subtitle = if (state.questions.isNotEmpty() && state.phase !is PlayerPhase.Submitted) {
                    "${state.answeredCount} of ${state.questions.size} answered"
                } else {
                    null
                },
                onBack = { if (running) confirmLeave = true else onExit() },
                actions = {
                    if (running) {
                        state.remainingSeconds?.let { TimerChip(it) }
                        IconButton(onClick = { showPalette = true }) { Icon(Icons.Outlined.GridView, contentDescription = "All questions") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val phase = state.phase) {
                PlayerPhase.Loading -> LoadingState()
                is PlayerPhase.Failed -> ErrorState(phase.message, onRetry = onRetry)
                PlayerPhase.Running, PlayerPhase.Submitting -> QuestionPage(
                    state = state,
                    onSelectOption = onSelectOption,
                    onText = onText,
                    onBlank = onBlank,
                    onToggleFlag = onToggleFlag,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onReview = { showPalette = true },
                )
                is PlayerPhase.Submitted -> ResultView(phase, onDone = onExit)
            }
            if (state.phase == PlayerPhase.Submitting) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    VCard {
                        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Submitting your answers…", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    if (showPalette && running) {
        ModalBottomSheet(onDismissRequest = { showPalette = false }) {
            Palette(
                state = state,
                onGoTo = {
                    onGoTo(it)
                    showPalette = false
                },
                onSubmit = {
                    showPalette = false
                    confirmSubmit = true
                },
            )
        }
    }

    if (confirmSubmit && running) {
        val unanswered = state.questions.size - state.answeredCount
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
            title = { Text("Submit your test?") },
            text = {
                Text(
                    buildString {
                        append("You answered ${state.answeredCount} of ${state.questions.size} questions.")
                        if (unanswered > 0) append(" $unanswered ${if (unanswered == 1) "is" else "are"} still blank.")
                        if (state.flagged.isNotEmpty()) append(" ${state.flagged.size} flagged for review.")
                        append(" You can't change answers after submitting.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSubmit = false
                    onSubmit()
                }) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("Keep going") } },
        )
    }

    if (confirmLeave && running) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the test?") },
            text = {
                Text(
                    if (state.remainingSeconds != null) {
                        "Your answers are saved and you can resume, but the timer keeps running."
                    } else {
                        "Your answers are saved and you can resume later."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onExit()
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }

    if (state.showLeaveWarning && running) {
        val limitReached = state.tabSwitches >= TestPlayerViewModel.MAX_LEAVES
        AlertDialog(
            onDismissRequest = onDismissLeaveWarning,
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = StatusRed) },
            title = { Text(if (limitReached) "Leave limit reached" else "You left the test") },
            text = {
                Text(
                    "This is a proctored test. Leaving the app has been reported to your instructor " +
                        "(${state.tabSwitches} of ${TestPlayerViewModel.MAX_LEAVES}). Please stay in the app until you submit.",
                )
            },
            confirmButton = { TextButton(onClick = onDismissLeaveWarning) { Text("Back to the test") } },
        )
    }
}

@Composable
private fun TimerChip(seconds: Long) {
    val low = seconds <= 60
    val color = if (low) StatusRed else Color.White
    Row(
        Modifier
            .padding(end = 4.dp)
            .background(if (low) Color.White else Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Timer, contentDescription = "Time left", tint = color, modifier = Modifier.size(16.dp))
        Text(" ${clockText(seconds)}", style = MaterialTheme.typography.labelLarge, color = color)
    }
}

internal fun clockText(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun QuestionPage(
    state: TestPlayerUiState,
    onSelectOption: (Question, Int) -> Unit,
    onText: (Question, String) -> Unit,
    onBlank: (Question, Int, String) -> Unit,
    onToggleFlag: (Question) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReview: () -> Unit,
) {
    val question = state.current ?: return
    val isLast = state.index == state.questions.lastIndex
    val flagged = question.key in state.flagged
    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (state.index + 1f) / state.questions.size },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = VintowsAccent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Question ${state.index + 1} of ${state.questions.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                question.marks?.let { TagChip("${formatMark(it)} ${if (it == 1.0) "mark" else "marks"}", VintowsBlue) }
                Spacer(Modifier.width(6.dp))
                TagChip(kindLabel(question.kind), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            if (question.kind != QuestionKind.FillBlank) {
                Text(question.text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            }
            if (question.imageUrl != null) {
                Text(
                    "This question has an image. Open the test on the web to see it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            AnswerInput(question, state.answers[question.key], enabled = state.phase == PlayerPhase.Running, onSelectOption, onText, onBlank)
        }

        // Bottom navigation
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onPrevious, enabled = state.index > 0, shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Back")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onToggleFlag(question) }) {
                    Icon(
                        Icons.Outlined.Flag,
                        contentDescription = if (flagged) "Remove flag" else "Flag for review",
                        tint = if (flagged) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (isLast) {
                    Button(onClick = onReview, shape = MaterialTheme.shapes.medium, colors = ButtonDefaults.buttonColors(containerColor = VintowsAccent)) {
                        Text("Review & submit")
                    }
                } else {
                    Button(onClick = onNext, shape = MaterialTheme.shapes.medium) {
                        Text("Next ")
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun kindLabel(kind: QuestionKind) = when (kind) {
    QuestionKind.SingleChoice -> "Single choice"
    QuestionKind.MultipleChoice -> "Multiple choice"
    QuestionKind.TrueFalse -> "True / False"
    QuestionKind.FillBlank -> "Fill in the blank"
    QuestionKind.ShortText -> "Short answer"
    QuestionKind.LongText -> "Long answer"
}

@Composable
private fun AnswerInput(
    question: Question,
    value: AnswerValue?,
    enabled: Boolean,
    onSelectOption: (Question, Int) -> Unit,
    onText: (Question, String) -> Unit,
    onBlank: (Question, Int, String) -> Unit,
) {
    when (question.kind) {
        QuestionKind.SingleChoice, QuestionKind.MultipleChoice -> {
            val selected = (value as? AnswerValue.Choices)?.options.orEmpty()
            if (question.kind == QuestionKind.MultipleChoice) {
                Text("Select all that apply", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                question.options.forEachIndexed { i, text ->
                    OptionCard(
                        letter = ('A' + i).toString(),
                        text = text,
                        selected = (i + 1) in selected,
                        enabled = enabled,
                        onClick = { onSelectOption(question, i + 1) },
                    )
                }
            }
        }
        QuestionKind.TrueFalse -> {
            val selected = (value as? AnswerValue.Choices)?.options?.firstOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("True" to 1, "False" to 2).forEach { (label, number) ->
                    OptionCard(
                        letter = label.take(1),
                        text = label,
                        selected = selected == number,
                        enabled = enabled,
                        onClick = { onSelectOption(question, number) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        QuestionKind.FillBlank -> {
            val values = (value as? AnswerValue.Blanks)?.values.orEmpty()
            val parts = question.blankParts
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                parts.forEachIndexed { i, part ->
                    if (part.isNotBlank()) Text(part.trim(), style = MaterialTheme.typography.titleMedium)
                    if (i < parts.lastIndex || parts.size == 1) {
                        OutlinedTextField(
                            value = values.getOrElse(i) { "" },
                            onValueChange = { onBlank(question, i, it) },
                            enabled = enabled,
                            singleLine = true,
                            label = { Text("Blank ${i + 1}") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                }
            }
        }
        QuestionKind.ShortText, QuestionKind.LongText -> {
            val long = question.kind == QuestionKind.LongText
            OutlinedTextField(
                value = (value as? AnswerValue.Text)?.text.orEmpty(),
                onValueChange = { onText(question, it) },
                enabled = enabled,
                singleLine = !long,
                minLines = if (long) 6 else 1,
                placeholder = { Text(if (long) "Write your answer…" else "Your answer") },
                modifier = Modifier.fillMaxWidth().then(if (long) Modifier.heightIn(min = 160.dp) else Modifier),
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}

@Composable
private fun OptionCard(
    letter: String,
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = VintowsBlue
    VCard(
        modifier = modifier.fillMaxWidth(),
        onClick = if (enabled) onClick else null,
        color = if (selected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, style = MaterialTheme.typography.titleSmall, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = accent)
        }
    }
}

@Composable
private fun Palette(state: TestPlayerUiState, onGoTo: (Int) -> Unit, onSubmit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("All questions", style = MaterialTheme.typography.titleLarge)
        Text(
            "${state.answeredCount} answered · ${state.questions.size - state.answeredCount} blank · ${state.flagged.size} flagged",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.questions.forEachIndexed { i, q ->
                val answered = state.isAnswered(q)
                val flagged = q.key in state.flagged
                val current = i == state.index
                val bg = when {
                    answered -> StatusGreen
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .then(if (current) Modifier.border(2.dp, VintowsNavy, RoundedCornerShape(12.dp)) else Modifier)
                        .clickable { onGoTo(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = if (answered) Color.White else MaterialTheme.colorScheme.onSurface)
                    if (flagged) {
                        Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(8.dp).background(StatusAmber, CircleShape))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(StatusGreen, "Answered")
            Legend(MaterialTheme.colorScheme.surfaceContainerHigh, "Blank")
            Legend(StatusAmber, "Flagged", dot = true)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = VintowsAccent),
        ) { Text("Submit test") }
    }
}

@Composable
private fun Legend(color: Color, label: String, dot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (dot) 8.dp else 14.dp).background(color, if (dot) CircleShape else RoundedCornerShape(4.dp)))
        Text("  $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultView(result: PlayerPhase.Submitted, onDone: () -> Unit) {
    val r = result.result
    val passed = r.passed
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(VintowsNavyDark, VintowsNavy, VintowsBlue)))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Test submitted", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                CircularProgressIndicator(
                    progress = { r.fraction ?: 0f },
                    modifier = Modifier.fillMaxSize(),
                    color = when (passed) {
                        true -> StatusGreen
                        false -> StatusRed
                        null -> VintowsAccent
                    },
                    trackColor = Color.White.copy(alpha = 0.16f),
                    strokeWidth = 12.dp,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (r.score != null) {
                        Text(formatMark(r.score), style = MaterialTheme.typography.displaySmall, color = Color.White)
                        r.maxScore?.let { Text("of ${formatMark(it)}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f)) }
                    } else {
                        Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            when {
                r.needsReview || r.score == null -> TagChip("Awaiting review", Color.White)
                passed == true -> TagChip("Passed", StatusGreen)
                passed == false -> TagChip("Not passed", StatusRed)
                else -> r.fraction?.let { TagChip("${(it * 100).toInt()}%", Color.White) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultStat("Answered", "${result.answered}/${result.total}", Modifier.weight(1f))
            ResultStat("Time taken", clockText(result.secondsUsed), Modifier.weight(1f))
        }
        if (r.needsReview) {
            Text(
                "Some answers are marked by your instructor. Your final score will update after review.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) { Text("Done") }
    }
}

@Composable
private fun ResultStat(label: String, value: String, modifier: Modifier) {
    VCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
