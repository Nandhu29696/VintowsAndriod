package com.vintows.app.feature.auth.domain

import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.session.Session

interface AuthRepository {
    /** Password login (licensed / admin users). Persists the session on success. */
    suspend fun login(email: String, password: String): NetworkResult<Session>

    /** Student sign-in step 1: email a 4-digit code. */
    suspend fun sendCode(email: String, name: String): NetworkResult<Unit>

    /** Student sign-in step 2. Existing students are logged in; new ones must register. */
    suspend fun verifyCode(email: String, code: String): NetworkResult<VerifyOutcome>

    /** Student sign-up after the email is verified. Persists the session on success. */
    suspend fun registerLearner(form: LearnerRegistration): NetworkResult<Session>

    suspend fun logout()
}

sealed interface VerifyOutcome {
    data class LoggedIn(val session: Session) : VerifyOutcome
    data object NeedsRegistration : VerifyOutcome
}
