package com.vintows.app.feature.auth.data

import com.vintows.app.core.network.ApiEnvelope
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiEnvelope<LoginData>
}
