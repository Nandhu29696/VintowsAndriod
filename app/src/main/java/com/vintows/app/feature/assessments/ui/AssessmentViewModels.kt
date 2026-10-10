package com.vintows.app.feature.assessments.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.session.Clock
import com.vintows.app.core.ui.Section
import com.vintows.app.core.ui.toSection
import com.vintows.app.feature.assessments.data.AttemptStore
import com.vintows.app.feature.assessments.domain.AnswerValue
import com.vintows.app.feature.assessments.domain.Assessment
import com.vintows.app.feature.assessments.domain.AssessmentsRepository
import com.vintows.app.feature.assessments.domain.AttemptCheck
import com.vintows.app.feature.assessments.domain.Question
import com.vintows.app.feature.assessments.domain.QuestionKind
import com.vintows.app.feature.assessments.domain.SavedAttempt
import com.vintows.app.feature.assessments.domain.TestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

// ---------- routes (arguments are read from SavedStateHandle by property name) ----------

@Serializable
data class TestIntroDestination(val assessmentId: String, val name: String, val levelCode: String, val recordId: String)

@Serializable
data class TestPlayerDestination(val assessmentId: String, val name: String)

// ---------- Tests tab ----------

data class TestsUiState(
    val tests: Section<List<Assessment>> = Section.Loading,
    val refreshing: Boolean = false,
)

@HiltViewModel
class TestsViewModel @Inject constructor(
    private val repository: AssessmentsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TestsUiState())
    val state: StateFlow<TestsUiState> = _state.asStateFlow()

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    fun retry() = load(refresh = false)

    private fun load(refresh: Boolean) {
        _state.update { if (refresh) it.copy(refreshing = true) else it.copy(tests = Section.Loading) }
        viewModelScope.launch {
            val result = repository.myTests().toSection()
            _state.update { s ->
                val keepOld = refresh && result is Section.Failed && s.tests is Section.Loaded
                s.copy(tests = if (keepOld) s.tests else result, refreshing = false)
            }
        }
    }
}

// ---------- intro ("Ready to begin?") ----------

data class TestIntroUiState(
    val name: String,
    val assessment: Section<Assessment> = Section.Loading,
    /** Failed with the server's reason (e.g. "This test has no questions yet.") when it can't start. */
    val check: Section<AttemptCheck> = Section.Loading,
    /** An attempt left running on this device. */
    val inProgress: Boolean = false,
) {
    val canStart: Boolean get() = inProgress || (check as? Section.Loaded)?.data?.canStart == true
}

@HiltViewModel
class TestIntroViewModel @Inject constructor(
    private val repository: AssessmentsRepository,
    private val store: AttemptStore,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val assessmentId = savedState.get<String>("assessmentId").orEmpty()
    private val levelCode = savedState.get<String>("levelCode").orEmpty()
    private val recordId = savedState.get<String>("recordId").orEmpty()

    private val _state = MutableStateFlow(TestIntroUiState(name = savedState.get<String>("name").orEmpty()))
    val state: StateFlow<TestIntroUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Called again when the screen returns from the player, so attempts left and resume state are fresh. */
    fun load() {
        _state.update { it.copy(assessment = Section.Loading, check = Section.Loading) }
        viewModelScope.launch {
            val inProgress = store.get(assessmentId) != null
            val assessment = when (val r = repository.testsForNode(levelCode, recordId)) {
                is NetworkResult.Success -> r.data.firstOrNull { it.id == assessmentId }
                    ?.let { Section.Loaded(it) }
                    ?: Section.Failed("This test is no longer available.")
                is NetworkResult.Error -> Section.Failed(r.message)
            }
            _state.update { it.copy(assessment = assessment, inProgress = inProgress) }
            val check = repository.check(assessmentId).toSection()
            _state.update { it.copy(check = check) }
        }
    }
}

// ---------- player ----------

sealed interface PlayerPhase {
    data object Loading : PlayerPhase
    data class Failed(val message: String) : PlayerPhase
    data object Running : PlayerPhase
    data object Submitting : PlayerPhase
    data class Submitted(val result: TestResult, val answered: Int, val total: Int, val secondsUsed: Long) : PlayerPhase
}

data class TestPlayerUiState(
    val name: String,
    val phase: PlayerPhase = PlayerPhase.Loading,
    val attemptId: String? = null,
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    val answers: Map<String, AnswerValue> = emptyMap(),
    val flagged: Set<String> = emptySet(),
    /** Null when the test has no time limit. */
    val remainingSeconds: Long? = null,
    val proctored: Boolean = false,
    val tabSwitches: Int = 0,
    /** Shown once after the learner comes back to the app during a proctored test. */
    val showLeaveWarning: Boolean = false,
    val transientError: String? = null,
) {
    val current: Question? get() = questions.getOrNull(index)
    val answeredCount: Int get() = questions.count { answers[it.key]?.isAnswered == true }
    fun isAnswered(q: Question) = answers[q.key]?.isAnswered == true
}

@HiltViewModel
class TestPlayerViewModel @Inject constructor(
    private val repository: AssessmentsRepository,
    private val store: AttemptStore,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val assessmentId = savedState.get<String>("assessmentId").orEmpty()

    private val _state = MutableStateFlow(TestPlayerUiState(name = savedState.get<String>("name").orEmpty()))
    val state: StateFlow<TestPlayerUiState> = _state.asStateFlow()

    private var startedAtSeconds: Long = 0
    private var deadlineSeconds: Long? = null
    private var timerJob: Job? = null
    private var saveJob: Job? = null

    init {
        open()
    }

    fun retry() = open()

    /**
     * Resumes the attempt saved on this device, or starts a new one. Starting is a server write that uses
     * up an attempt, so it only happens when nothing is saved.
     */
    private fun open() {
        _state.update { it.copy(phase = PlayerPhase.Loading) }
        viewModelScope.launch {
            val saved = store.get(assessmentId)
            val result = if (saved != null) repository.attempt(saved.attemptId) else repository.start(assessmentId)
            when (result) {
                is NetworkResult.Error -> {
                    // A saved attempt the server no longer knows (submitted elsewhere, expired) is dropped.
                    if (saved != null) store.clear(assessmentId)
                    _state.update { it.copy(phase = PlayerPhase.Failed(result.message)) }
                }
                is NetworkResult.Success -> {
                    val attempt = result.data
                    if (attempt.questions.isEmpty()) {
                        _state.update { it.copy(phase = PlayerPhase.Failed("This test has no questions yet.")) }
                        return@launch
                    }
                    startedAtSeconds = saved?.startedAtMillis?.div(1000)
                        ?: attempt.startedAtMillis?.div(1000)
                        ?: clock.nowEpochSeconds()
                    deadlineSeconds = attempt.timeLimitMinutes?.let { startedAtSeconds + it * 60L }
                    if (saved == null) store.save(assessmentId, SavedAttempt(attempt.id, startedAtSeconds * 1000))
                    _state.update {
                        it.copy(
                            phase = PlayerPhase.Running,
                            attemptId = attempt.id,
                            questions = attempt.questions,
                            answers = saved?.answers.orEmpty(),
                            flagged = saved?.flagged.orEmpty(),
                            proctored = attempt.proctored,
                            remainingSeconds = remaining(),
                        )
                    }
                    startTimer()
                }
            }
        }
    }

    // ----- answering -----

    fun goTo(index: Int) = _state.update { it.copy(index = index.coerceIn(0, (it.questions.size - 1).coerceAtLeast(0))) }

    fun next() = goTo(_state.value.index + 1)

    fun previous() = goTo(_state.value.index - 1)

    /** Option numbers are 1-based. Single choice and true/false replace; multiple choice toggles. */
    fun selectOption(question: Question, option: Int) = answer(question) { old ->
        val current = (old as? AnswerValue.Choices)?.options.orEmpty()
        val next = if (question.kind == QuestionKind.MultipleChoice) {
            if (option in current) current - option else current + option
        } else {
            listOf(option)
        }
        AnswerValue.Choices(next.sorted())
    }

    fun setText(question: Question, text: String) = answer(question) { AnswerValue.Text(text) }

    fun setBlank(question: Question, blank: Int, text: String) = answer(question) { old ->
        val values = (old as? AnswerValue.Blanks)?.values.orEmpty().toMutableList()
        while (values.size < question.blankCount) values += ""
        if (blank in values.indices) values[blank] = text
        AnswerValue.Blanks(values)
    }

    fun toggleFlag(question: Question) {
        _state.update { s -> s.copy(flagged = if (question.key in s.flagged) s.flagged - question.key else s.flagged + question.key) }
        persist()
    }

    private fun answer(question: Question, change: (AnswerValue?) -> AnswerValue) {
        if (_state.value.phase != PlayerPhase.Running) return
        _state.update { s -> s.copy(answers = s.answers + (question.key to change(s.answers[question.key]))) }
        persist()
    }

    /** Saves progress shortly after the last change, so typing doesn't write on every key. */
    private fun persist() {
        val attemptId = _state.value.attemptId ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            val s = _state.value
            store.save(assessmentId, SavedAttempt(attemptId, startedAtSeconds * 1000, s.answers, s.flagged))
        }
    }

    // ----- timer and submit -----

    private fun remaining(): Long? = deadlineSeconds?.let { (it - clock.nowEpochSeconds()).coerceAtLeast(0) }

    private fun startTimer() {
        timerJob?.cancel()
        if (deadlineSeconds == null) return
        timerJob = viewModelScope.launch {
            while (true) {
                val left = remaining() ?: return@launch
                _state.update { it.copy(remainingSeconds = left) }
                if (left <= 0L) {
                    submit()
                    return@launch
                }
                delay(1_000)
            }
        }
    }

    fun submit() {
        val s = _state.value
        val attemptId = s.attemptId ?: return
        if (s.phase != PlayerPhase.Running) return
        _state.update { it.copy(phase = PlayerPhase.Submitting) }
        viewModelScope.launch {
            saveJob?.cancel()
            when (val result = repository.submit(attemptId, s.questions, s.answers)) {
                is NetworkResult.Success -> {
                    timerJob?.cancel()
                    store.clear(assessmentId)
                    _state.update {
                        it.copy(
                            phase = PlayerPhase.Submitted(
                                result = result.data,
                                answered = s.answeredCount,
                                total = s.questions.size,
                                secondsUsed = (clock.nowEpochSeconds() - startedAtSeconds).coerceAtLeast(0),
                            ),
                        )
                    }
                }
                // Answers stay saved on the device; the learner can try again.
                is NetworkResult.Error -> _state.update { it.copy(phase = PlayerPhase.Running, transientError = result.message) }
            }
        }
    }

    fun consumeTransientError() = _state.update { it.copy(transientError = null) }

    // ----- proctoring -----

    /** The app went to the background during a proctored test: report it like the web's tab-switch rule. */
    fun onAppBackgrounded() {
        val s = _state.value
        val attemptId = s.attemptId ?: return
        if (!s.proctored || s.phase != PlayerPhase.Running) return
        val count = s.tabSwitches + 1
        _state.update { it.copy(tabSwitches = count, showLeaveWarning = true) }
        viewModelScope.launch { repository.reportProctoringEvent(attemptId, "tab_switch", "$count/$MAX_LEAVES") }
    }

    fun dismissLeaveWarning() = _state.update { it.copy(showLeaveWarning = false) }

    companion object {
        /** Same limit the web player shows. */
        const val MAX_LEAVES = 3
        private const val SAVE_DELAY_MS = 400L
    }
}
