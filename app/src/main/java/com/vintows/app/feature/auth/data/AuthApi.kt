package com.vintows.app.feature.auth.data

import com.vintows.app.core.network.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiEnvelope<LoginData>

    /** Emails a 4-digit code. Works for new and existing students. */
    @POST("emailverification/sendmailverification")
    suspend fun sendCode(@Body request: SendCodeRequest): ApiEnvelope<JsonElement>

    @POST("emailverification/verify")
    suspend fun verifyCode(@Body request: VerifyCodeRequest): ApiEnvelope<VerifyCodeData>

    @POST("auth/register")
    suspend fun registerLearner(@Body request: RegisterLearnerRequest): ApiEnvelope<RegisterData>
}
