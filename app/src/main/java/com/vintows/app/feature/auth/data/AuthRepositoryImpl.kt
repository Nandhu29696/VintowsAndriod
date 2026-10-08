package com.vintows.app.feature.auth.data

import com.vintows.app.core.di.ApplicationScope
import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.ErrorKind
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.push.PushRegistrar
import com.vintows.app.core.rbac.MenuRepository
import com.vintows.app.core.session.JwtDecoder
import com.vintows.app.core.session.Session
import com.vintows.app.core.session.SessionManager
import com.vintows.app.feature.auth.domain.AuthRepository
import com.vintows.app.feature.auth.domain.LearnerRegistration
import com.vintows.app.feature.auth.domain.VerifyOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val apiCaller: ApiCaller,
    private val jwtDecoder: JwtDecoder,
    private val sessionManager: SessionManager,
    private val menuRepository: MenuRepository,
    private val push: PushRegistrar,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : AuthRepository {

    override suspend fun login(email: String, password: String): NetworkResult<Session> =
        when (val result = apiCaller.call { api.login(LoginRequest.of(email.trim(), password)) }) {
            is NetworkResult.Error -> result
            is NetworkResult.Success -> with(result.data) {
                startSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    userId = user.id,
                    email = user.email ?: email.trim(),
                    roleId = user.roleId,
                    role = null,
                    scope = scope,
                    tenantId = tenantId,
                    dbName = dbName,
                )
            }
        }

    override suspend fun sendCode(email: String, name: String): NetworkResult<Unit> =
        apiCaller.callUnit { api.sendCode(SendCodeRequest(email.trim(), name.trim())) }

    override suspend fun verifyCode(email: String, code: String): NetworkResult<VerifyOutcome> {
        val result = apiCaller.call { api.verifyCode(VerifyCodeRequest(email.trim(), code.trim())) }
        if (result is NetworkResult.Error) return result
        val data = (result as NetworkResult.Success).data

        // The web also treats `data.success == false` as a wrong/expired code.
        if (data.success == false) {
            return NetworkResult.Error(ErrorKind.Client, data.message ?: "Invalid or expired code.")
        }
        if (data.action == ACTION_LOGIN && data.accessToken != null) {
            val session = startSession(
                accessToken = data.accessToken,
                refreshToken = data.refreshToken,
                userId = data.user?.id,
                email = data.user?.email ?: email.trim(),
                roleId = data.user?.roleId,
                role = data.role ?: data.user?.role,
                scope = data.scope,
                tenantId = data.tenantId,
                dbName = data.dbName,
            )
            return when (session) {
                is NetworkResult.Success -> NetworkResult.Success(VerifyOutcome.LoggedIn(session.data))
                is NetworkResult.Error -> session
            }
        }
        return NetworkResult.Success(VerifyOutcome.NeedsRegistration)
    }

    override suspend fun registerLearner(form: LearnerRegistration): NetworkResult<Session> {
        val request = RegisterLearnerRequest(
            email = form.email.trim(),
            fullName = form.fullName.trim(),
            mobileNo = form.phone.trim(),
            currentRole = form.currentRole,
            experienceYears = form.experienceYears,
            techSkills = form.techSkills,
            softSkills = form.softSkills,
            targetRole = form.targetRole.trim(),
            upskillInterest = form.upskillInterest,
            placementReady = form.placementReady,
            weeklyHours = form.weeklyHours,
            notes = form.notes.trim(),
            role = RegisterLearnerRequest.ROLE_LEARNER,
            googleSignedUp = false,
        )
        val result = apiCaller.call { api.registerLearner(request) }
        if (result is NetworkResult.Error) return result
        val data = (result as NetworkResult.Success).data
        val token = data.accessToken
            ?: return NetworkResult.Error(
                ErrorKind.Unknown,
                "Your account was created. Sign in with your email code to continue.",
            )
        return startSession(
            accessToken = token,
            refreshToken = data.refreshToken,
            userId = data.user?.id,
            email = data.user?.email ?: request.email,
            roleId = data.user?.roleId,
            role = data.user?.role,
            scope = data.scope,
            tenantId = data.tenantId,
            dbName = null,
        )
    }

    override suspend fun logout() {
        push.unregisterDevice() // needs the token, so before the session is cleared
        menuRepository.clearCache()
        sessionManager.clear()
    }

    /**
     * Builds the session from the response plus the JWT (role name and expiry live in the JWT),
     * saves it, and registers the device for push in the background.
     */
    private suspend fun startSession(
        accessToken: String,
        refreshToken: String?,
        userId: JsonElement?,
        email: String,
        roleId: Int?,
        role: String?,
        scope: String?,
        tenantId: JsonElement?,
        dbName: String?,
    ): NetworkResult<Session> {
        val claims = jwtDecoder.decode(accessToken)
        val id = userId.asIdString() ?: claims?.userId
            ?: return NetworkResult.Error(ErrorKind.Unknown, "Unexpected sign-in response. Please try again.")
        val session = Session(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = id,
            email = email,
            roleId = roleId,
            role = claims?.role ?: role,
            scope = scope ?: claims?.scope,
            tenantId = tenantId.asIdString() ?: claims?.tenantId,
            dbName = dbName,
            expiresAtEpochSeconds = claims?.expiresAt,
        )
        sessionManager.save(session)
        appScope.launch { push.registerDevice() }
        return NetworkResult.Success(session)
    }

    private companion object {
        const val ACTION_LOGIN = "login"
    }
}
