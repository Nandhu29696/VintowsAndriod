package com.vintows.app.feature.auth.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.feature.auth.domain.AuthRepository
import com.vintows.app.feature.auth.domain.LoginValidator
import com.vintows.app.feature.auth.domain.VerifyOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SignInStage { Email, Code }

data class StudentSignInUiState(
    val stage: SignInStage = SignInStage.Email,
    val email: String = "",
    val name: String = "",
    val emailError: String? = null,
    val code: String = "",
    val sending: Boolean = false,
    val verifying: Boolean = false,
    val error: String? = null,
    val resendInSeconds: Int = 0,
    /** One-shot: email verified but no account yet → open the sign-up form. */
    val registerEmail: String? = null,
)

/**
 * Student sign-in / sign-up entry, same flow as the web:
 * email → 4-digit code → logged in (existing student) or sign-up form (new student).
 */
@HiltViewModel
class StudentSignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StudentSignInUiState())
    val state: StateFlow<StudentSignInUiState> = _state.asStateFlow()
    private var countdown: Job? = null

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }

    fun onCodeChange(value: String) {
        val digits = value.filter(Char::isDigit).take(CODE_LENGTH)
        _state.update { it.copy(code = digits, error = null) }
        if (digits.length == CODE_LENGTH) verify()
    }

    fun sendCode() {
        val s = _state.value
        if (s.sending) return
        LoginValidator.emailError(s.email)?.let { error ->
            _state.update { it.copy(emailError = error) }
            return
        }
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            // The server uses the name only to greet the student in the email.
            when (val result = authRepository.sendCode(s.email, s.name.ifBlank { DEFAULT_NAME })) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(sending = false, stage = SignInStage.Code, code = "") }
                    startCountdown()
                }
                is NetworkResult.Error -> _state.update { it.copy(sending = false, error = result.message) }
            }
        }
    }

    fun resend() {
        if (_state.value.resendInSeconds > 0) return
        sendCode()
    }

    fun verify() {
        val s = _state.value
        if (s.verifying || s.code.length != CODE_LENGTH) return
        _state.update { it.copy(verifying = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.verifyCode(s.email, s.code)) {
                // Logged in: the session is saved and the app shell switches to Home on its own.
                is NetworkResult.Success -> when (result.data) {
                    is VerifyOutcome.LoggedIn -> _state.update { it.copy(verifying = false) }
                    VerifyOutcome.NeedsRegistration -> _state.update { it.copy(verifying = false, registerEmail = s.email.trim()) }
                }
                is NetworkResult.Error -> _state.update { it.copy(verifying = false, code = "", error = result.message) }
            }
        }
    }

    fun changeEmail() {
        countdown?.cancel()
        _state.update { it.copy(stage = SignInStage.Email, code = "", error = null, resendInSeconds = 0) }
    }

    fun onRegisterOpened() = _state.update { it.copy(registerEmail = null) }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (left in RESEND_SECONDS downTo 0) {
                _state.update { it.copy(resendInSeconds = left) }
                if (left > 0) delay(1_000)
            }
        }
    }

    companion object {
        const val CODE_LENGTH = 4
        const val RESEND_SECONDS = 60
        private const val DEFAULT_NAME = "Student"
    }
}
