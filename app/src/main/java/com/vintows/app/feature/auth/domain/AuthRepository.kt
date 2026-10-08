package com.vintows.app.feature.auth.domain

import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.session.Session

interface AuthRepository {
    /** Logs in and persists the session on success. */
    suspend fun login(email: String, password: String): NetworkResult<Session>

    suspend fun logout()
}
