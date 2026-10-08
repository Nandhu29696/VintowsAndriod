package com.vintows.app.feature.auth.data

import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.rbac.MenuRepository
import com.vintows.app.core.session.JwtDecoder
import com.vintows.app.core.session.Session
import com.vintows.app.core.session.SessionManager
import com.vintows.app.feature.auth.domain.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val apiCaller: ApiCaller,
    private val jwtDecoder: JwtDecoder,
    private val sessionManager: SessionManager,
    private val menuRepository: MenuRepository,
) : AuthRepository {

    override suspend fun login(email: String, password: String): NetworkResult<Session> {
        val result = apiCaller.call { api.login(LoginRequest.of(email.trim(), password)) }
        return when (result) {
            is NetworkResult.Error -> result
            is NetworkResult.Success -> {
                val session = result.data.toSession(jwtDecoder)
                sessionManager.save(session)
                NetworkResult.Success(session)
            }
        }
    }

    override suspend fun logout() {
        // Phase 3: unregister the FCM token here, before the session is cleared.
        menuRepository.clearCache()
        sessionManager.clear()
    }
}

/**
 * The login body has no role name, so it comes from the JWT (`role`, e.g. "SuperAdmin").
 * Expiry also comes from the JWT `exp` rather than parsing `expiresIn: "7d"`.
 */
internal fun LoginData.toSession(jwtDecoder: JwtDecoder): Session {
    val claims = jwtDecoder.decode(accessToken)
    return Session(
        accessToken = accessToken,
        refreshToken = refreshToken,
        userId = user.id,
        email = user.email,
        roleId = user.roleId,
        role = claims?.role,
        scope = scope ?: claims?.scope,
        tenantId = tenantId.asIdString() ?: claims?.tenantId,
        dbName = dbName,
        expiresAtEpochSeconds = claims?.expiresAt,
    )
}
