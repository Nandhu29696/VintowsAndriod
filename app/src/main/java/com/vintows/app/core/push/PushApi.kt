package com.vintows.app.core.push

import com.vintows.app.core.network.ApiEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.POST

/** Same endpoints the web uses for its FCM tokens (`platform: "web"` there). */
interface PushApi {
    @POST("notifications/register-token")
    suspend fun register(@Body request: PushTokenRequest): ApiEnvelope<JsonElement>

    @HTTP(method = "DELETE", path = "notifications/register-token", hasBody = true)
    suspend fun unregister(@Body request: PushTokenRequest): ApiEnvelope<JsonElement>
}

@Serializable
data class PushTokenRequest(
    val token: String,
    val platform: String? = null,
) {
    companion object {
        const val PLATFORM_ANDROID = "android"
    }
}
