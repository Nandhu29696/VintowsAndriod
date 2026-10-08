package com.vintows.app.feature.auth

import androidx.lifecycle.SavedStateHandle
import com.vintows.app.core.network.ErrorKind
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.session.Session
import com.vintows.app.feature.auth.domain.AuthRepository
import com.vintows.app.feature.auth.domain.LearnerRegistration
import com.vintows.app.feature.auth.domain.VerifyOutcome
import com.vintows.app.feature.auth.ui.student.RegisterViewModel
import com.vintows.app.feature.auth.ui.student.SignInStage
import com.vintows.app.feature.auth.ui.student.StudentSignInViewModel
import com.vintows.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudentFlowViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private class FakeAuth : AuthRepository {
        var sendResult: NetworkResult<Unit> = NetworkResult.Success(Unit)
        var verifyResult: NetworkResult<VerifyOutcome> = NetworkResult.Success(VerifyOutcome.NeedsRegistration)
        var registerResult: NetworkResult<Session> = NetworkResult.Success(Session(accessToken = "t", userId = "u", email = "s@example.com"))
        var sentTo: Pair<String, String>? = null
        var verified: Pair<String, String>? = null
        var registered: LearnerRegistration? = null

        override suspend fun login(email: String, password: String) = error("not used")
        override suspend fun sendCode(email: String, name: String): NetworkResult<Unit> { sentTo = email to name; return sendResult }
        override suspend fun verifyCode(email: String, code: String): NetworkResult<VerifyOutcome> { verified = email to code; return verifyResult }
        override suspend fun registerLearner(form: LearnerRegistration): NetworkResult<Session> { registered = form; return registerResult }
        override suspend fun logout() = Unit
    }

    private val auth = FakeAuth()

    // ---------- sign in ----------

    @Test
    fun `invalid email is rejected without calling the API`() = runTest(main.dispatcher) {
        val vm = StudentSignInViewModel(auth)
        vm.onEmailChange("nope")

        vm.sendCode()
        runCurrent()

        assertEquals("Enter a valid email address", vm.state.value.emailError)
        assertNull(auth.sentTo)
    }

    @Test
    fun `sending the code moves to the code step and starts a 60s resend timer`() = runTest(main.dispatcher) {
        val vm = StudentSignInViewModel(auth)
        vm.onEmailChange("student@example.com")

        vm.sendCode()
        runCurrent()

        assertEquals(SignInStage.Code, vm.state.value.stage)
        assertEquals("student@example.com" to "Student", auth.sentTo) // default greeting name
        assertEquals(60, vm.state.value.resendInSeconds)
        advanceTimeBy(30_001)
        assertEquals(30, vm.state.value.resendInSeconds)
        advanceTimeBy(30_000)
        assertEquals(0, vm.state.value.resendInSeconds)
    }

    @Test
    fun `typing the 4th digit verifies automatically and opens sign-up for new students`() = runTest(main.dispatcher) {
        val vm = StudentSignInViewModel(auth)
        vm.onEmailChange("new@example.com")
        vm.sendCode()
        runCurrent()

        vm.onCodeChange("12a3") // non-digits are dropped
        assertEquals("123", vm.state.value.code)
        vm.onCodeChange("1234")
        runCurrent()

        assertEquals("new@example.com" to "1234", auth.verified)
        assertEquals("new@example.com", vm.state.value.registerEmail)
        vm.onRegisterOpened()
        assertNull(vm.state.value.registerEmail)
    }

    @Test
    fun `wrong code shows the message and clears the input`() = runTest(main.dispatcher) {
        auth.verifyResult = NetworkResult.Error(ErrorKind.Client, "Invalid verification code")
        val vm = StudentSignInViewModel(auth)
        vm.onEmailChange("student@example.com")
        vm.sendCode()
        runCurrent()

        vm.onCodeChange("9999")
        runCurrent()

        assertEquals("Invalid verification code", vm.state.value.error)
        assertEquals("", vm.state.value.code)
        assertNull(vm.state.value.registerEmail)
    }

    // ---------- sign up ----------

    private fun registerVm() = RegisterViewModel(SavedStateHandle(mapOf(RegisterViewModel.ARG_EMAIL to "s@example.com")), auth)

    @Test
    fun `step 1 requires name and phone`() = runTest(main.dispatcher) {
        val vm = registerVm()

        vm.next()

        assertEquals(0, vm.state.value.step)
        assertEquals(setOf("fullName", "phone"), vm.state.value.errors.keys)
    }

    @Test
    fun `walks through the four steps and submits with merged other skills`() = runTest(main.dispatcher) {
        val vm = registerVm()
        vm.update { copy(fullName = "Asha", phone = "9876543210") }
        vm.next()
        vm.toggleTechSkill("Python")
        vm.toggleTechSkill("SQL")
        vm.toggleTechSkill("SQL") // toggled off again
        vm.onOtherTechSkillsChange(" Kotlin, ,Python ")
        vm.next()
        vm.toggleUpskill("Data Science")
        vm.next()
        assertTrue(vm.state.value.isLastStep)

        vm.next()
        runCurrent()

        val sent = auth.registered!!
        assertEquals("s@example.com", sent.email)
        assertEquals(listOf("Python", "Kotlin"), sent.techSkills)
        assertEquals(listOf("Data Science"), sent.upskillInterest)
        assertEquals(false, vm.state.value.submitting)
    }

    @Test
    fun `back moves to the previous step, and signals exit on the first`() = runTest(main.dispatcher) {
        val vm = registerVm()
        vm.update { copy(fullName = "Asha", phone = "9876543210") }
        vm.next()

        assertTrue(vm.back())
        assertEquals(0, vm.state.value.step)
        assertEquals(false, vm.back())
    }

    @Test
    fun `server error on submit is shown`() = runTest(main.dispatcher) {
        auth.registerResult = NetworkResult.Error(ErrorKind.Client, "An account with this email already exists", 409)
        val vm = registerVm()
        vm.update { copy(fullName = "Asha", phone = "9876543210") }
        repeat(4) { vm.next() }
        runCurrent()

        assertEquals("An account with this email already exists", vm.state.value.submitError)
    }
}
