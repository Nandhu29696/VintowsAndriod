package com.vintows.app.feature.auth

import com.vintows.app.core.network.ErrorKind
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.session.Session
import com.vintows.app.feature.auth.domain.AuthRepository
import com.vintows.app.feature.auth.domain.LoginValidator
import com.vintows.app.feature.auth.ui.LoginViewModel
import com.vintows.app.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeAuthRepository : AuthRepository {
        var calls = 0
        var result: NetworkResult<Session> = NetworkResult.Success(Session(accessToken = "t", userId = 3, email = "admin@example.com"))
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun login(email: String, password: String): NetworkResult<Session> {
            calls++
            gate?.await()
            return result
        }

        override suspend fun logout() = Unit
    }

    private val repo = FakeAuthRepository()
    private val viewModel = LoginViewModel(repo)

    private fun fill(email: String, password: String) {
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
    }

    @Test
    fun `invalid input shows field errors and does not call the API`() {
        fill("not-an-email", "123")

        viewModel.login()

        val state = viewModel.state.value
        assertEquals("Enter a valid email address", state.emailError)
        assertEquals("Password must be at least 6 characters", state.passwordError)
        assertEquals(0, repo.calls)
    }

    @Test
    fun `typing clears the field error`() {
        viewModel.login()
        assertNotNull(viewModel.state.value.emailError)

        viewModel.onEmailChange("q")

        assertNull(viewModel.state.value.emailError)
    }

    @Test
    fun `successful login sets loggedIn and clears the password`() {
        fill("admin@example.com", "Secret@123")

        viewModel.login()

        val state = viewModel.state.value
        assertTrue(state.loggedIn)
        assertFalse(state.isLoading)
        assertEquals("", state.password)
    }

    @Test
    fun `failed login shows the server message`() {
        repo.result = NetworkResult.Error(ErrorKind.Unauthorized, "Invalid email or password", 401)
        fill("admin@example.com", "Secret@123")

        viewModel.login()

        val state = viewModel.state.value
        assertFalse(state.loggedIn)
        assertEquals("Invalid email or password", state.errorMessage)
    }

    @Test
    fun `double tap while loading sends one request`() {
        repo.gate = CompletableDeferred()
        fill("admin@example.com", "Secret@123")

        viewModel.login()
        assertTrue(viewModel.state.value.isLoading)
        viewModel.login()
        repo.gate!!.complete(Unit)

        assertEquals(1, repo.calls)
        assertTrue(viewModel.state.value.loggedIn)
    }

    @Test
    fun `validator rules match the web form`() {
        assertEquals("Email is required", LoginValidator.emailError(" "))
        assertNull(LoginValidator.emailError("admin@example.com"))
        assertEquals("Password is required", LoginValidator.passwordError(""))
        assertNull(LoginValidator.passwordError("123456"))
    }
}
