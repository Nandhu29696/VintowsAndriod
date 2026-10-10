package com.vintows.app.feature.assessments

import androidx.lifecycle.SavedStateHandle
import com.vintows.app.core.network.ErrorKind
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.session.Clock
import com.vintows.app.core.session.Session
import com.vintows.app.core.session.SessionProvider
import com.vintows.app.feature.assessments.data.AttemptStore
import com.vintows.app.feature.assessments.domain.AnswerValue
import com.vintows.app.feature.assessments.domain.Assessment
import com.vintows.app.feature.assessments.domain.AssessmentsRepository
import com.vintows.app.feature.assessments.domain.Attempt
import com.vintows.app.feature.assessments.domain.AttemptCheck
import com.vintows.app.feature.assessments.domain.Question
import com.vintows.app.feature.assessments.domain.QuestionKind
import com.vintows.app.feature.assessments.domain.SavedAttempt
import com.vintows.app.feature.assessments.domain.TestResult
import com.vintows.app.feature.assessments.ui.PlayerPhase
import com.vintows.app.feature.assessments.ui.TestPlayerViewModel
import com.vintows.app.feature.assessments.ui.clockText
import com.vintows.app.testing.InMemoryPreferencesDataStore
import com.vintows.app.testing.MainDispatcherRule
import com.vintows.app.testing.testJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TestPlayerViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private var now = 1_000_000L
    private val clock = Clock { now }
    private val sessions = object : SessionProvider {
        override fun current() = Session(accessToken = "t", userId = "u-1", email = "s@example.com", role = "Learner", scope = "global")
        override fun onUnauthorized() = Unit
    }
    private val store = AttemptStore(InMemoryPreferencesDataStore(), sessions, testJson)

    private val single = Question("1", JsonPrimitive(1), QuestionKind.SingleChoice, "2 + 2", listOf("3", "4"))
    private val multi = Question("2", JsonPrimitive(2), QuestionKind.MultipleChoice, "Primes", listOf("2", "4", "5"))
    private val blank = Question("3", JsonPrimitive(3), QuestionKind.FillBlank, "___ is the capital of ___")

    private inner class FakeRepository : AssessmentsRepository {
        var attempt = Attempt("att-1", listOf(single, multi, blank), timeLimitMinutes = 10, startedAtMillis = null, maxAttempts = 1, attemptsRemaining = 0, proctored = true)
        var submitResult: NetworkResult<TestResult> = NetworkResult.Success(TestResult(2.0, 3.0, passed = true, needsReview = false))
        var resumeResult: NetworkResult<Attempt>? = null
        val calls = mutableListOf<String>()
        var submitted: Map<String, AnswerValue>? = null

        override suspend fun myTests() = NetworkResult.Success(emptyList<Assessment>())
        override suspend fun testsForNode(levelCode: String, recordId: String, nodeName: String?) = NetworkResult.Success(emptyList<Assessment>())
        override suspend fun check(assessmentId: String) = NetworkResult.Success(AttemptCheck(true, 1, 1))
        override suspend fun start(assessmentId: String): NetworkResult<Attempt> = NetworkResult.Success(attempt).also { calls += "start" }
        override suspend fun attempt(attemptId: String): NetworkResult<Attempt> =
            (resumeResult ?: NetworkResult.Success(attempt)).also { calls += "get:$attemptId" }
        override suspend fun submit(attemptId: String, questions: List<Question>, answers: Map<String, AnswerValue>): NetworkResult<TestResult> {
            calls += "submit:$attemptId"
            submitted = answers
            return submitResult
        }
        override suspend fun reportProctoringEvent(attemptId: String, type: String, detail: String) {
            calls += "event:$type:$detail"
        }
    }

    private val repo = FakeRepository()

    private fun newViewModel() = TestPlayerViewModel(repo, store, clock, SavedStateHandle(mapOf("assessmentId" to "a1", "name" to "Unit test")))

    private fun advance(ms: Long) = main.dispatcher.scheduler.advanceTimeBy(ms)

    private fun saved(): SavedAttempt? = runBlocking { store.get("a1") }

    @Test
    fun `a new attempt is started and remembered on the device`() {
        val vm = newViewModel()

        assertEquals(listOf("start"), repo.calls)
        assertEquals(PlayerPhase.Running, vm.state.value.phase)
        assertEquals(600L, vm.state.value.remainingSeconds)
        assertEquals("att-1", saved()?.attemptId)
        assertEquals(now * 1000, saved()?.startedAtMillis)
    }

    @Test
    fun `a saved attempt is resumed with its answers and original start time`() {
        runBlocking { store.save("a1", SavedAttempt("att-1", (now - 120) * 1000, mapOf("1" to AnswerValue.Choices(listOf(2))), setOf("2"))) }

        val vm = newViewModel()

        assertEquals(listOf("get:att-1"), repo.calls)
        assertEquals(AnswerValue.Choices(listOf(2)), vm.state.value.answers["1"])
        assertEquals(setOf("2"), vm.state.value.flagged)
        assertEquals(480L, vm.state.value.remainingSeconds) // 10 min limit, 2 min already used
    }

    @Test
    fun `a saved attempt the server no longer has is dropped`() {
        runBlocking { store.save("a1", SavedAttempt("gone", now * 1000)) }
        repo.resumeResult = NetworkResult.Error(ErrorKind.NotFound, "Attempt not found")

        val vm = newViewModel()

        assertEquals(PlayerPhase.Failed("Attempt not found"), vm.state.value.phase)
        assertNull(saved())
    }

    @Test
    fun `single choice replaces, multiple choice toggles, blanks fill by position, all saved after a pause`() {
        val vm = newViewModel()

        vm.selectOption(single, 1)
        vm.selectOption(single, 2)
        vm.selectOption(multi, 3)
        vm.selectOption(multi, 1)
        vm.selectOption(multi, 3)
        vm.setBlank(blank, 1, "Italy")

        val answers = vm.state.value.answers
        assertEquals(AnswerValue.Choices(listOf(2)), answers["1"])
        assertEquals(AnswerValue.Choices(listOf(1)), answers["2"])
        assertEquals(AnswerValue.Blanks(listOf("", "Italy")), answers["3"])
        assertEquals(3, vm.state.value.answeredCount)

        assertTrue(saved()?.answers.isNullOrEmpty())
        advance(500)
        assertEquals(answers, saved()?.answers)
    }

    @Test
    fun `navigation stays inside the question list`() {
        val vm = newViewModel()

        vm.previous()
        assertEquals(0, vm.state.value.index)
        vm.goTo(10)
        assertEquals(2, vm.state.value.index)
        vm.next()
        assertEquals(2, vm.state.value.index)
    }

    @Test
    fun `time running out submits automatically`() {
        val vm = newViewModel()
        vm.selectOption(single, 2)

        now += 600
        advance(1_100)

        assertTrue(repo.calls.contains("submit:att-1"))
        val phase = vm.state.value.phase as PlayerPhase.Submitted
        assertEquals(1, phase.answered)
        assertEquals(3, phase.total)
        assertEquals(600L, phase.secondsUsed)
        assertNull(saved())
    }

    @Test
    fun `a failed submit keeps the answers and lets the learner retry`() {
        repo.submitResult = NetworkResult.Error(ErrorKind.Network, "No internet connection.")
        val vm = newViewModel()
        vm.selectOption(single, 2)
        advance(500)

        vm.submit()

        assertEquals(PlayerPhase.Running, vm.state.value.phase)
        assertEquals("No internet connection.", vm.state.value.transientError)
        assertEquals("att-1", saved()?.attemptId)

        repo.submitResult = NetworkResult.Success(TestResult(1.0, 3.0, passed = false, needsReview = false))
        vm.submit()
        assertTrue(vm.state.value.phase is PlayerPhase.Submitted)
    }

    @Test
    fun `leaving a proctored test is reported and warned about`() {
        val vm = newViewModel()

        vm.onAppBackgrounded()
        vm.dismissLeaveWarning()
        vm.onAppBackgrounded()

        assertEquals(listOf("start", "event:tab_switch:1/3", "event:tab_switch:2/3"), repo.calls)
        assertEquals(2, vm.state.value.tabSwitches)
        assertTrue(vm.state.value.showLeaveWarning)
    }

    @Test
    fun `leaving an unproctored test is not reported`() {
        repo.attempt = repo.attempt.copy(proctored = false, timeLimitMinutes = null)
        val vm = newViewModel()

        vm.onAppBackgrounded()

        assertEquals(listOf("start"), repo.calls)
        assertFalse(vm.state.value.showLeaveWarning)
        assertNull(vm.state.value.remainingSeconds)
    }

    @Test
    fun `an empty test can't be opened`() {
        repo.attempt = repo.attempt.copy(questions = emptyList())

        val vm = newViewModel()

        assertEquals(PlayerPhase.Failed("This test has no questions yet."), vm.state.value.phase)
    }

    @Test
    fun `timer text`() {
        assertEquals("09:05", clockText(545))
        assertEquals("1:00:00", clockText(3600))
        assertEquals("00:00", clockText(0))
    }
}
