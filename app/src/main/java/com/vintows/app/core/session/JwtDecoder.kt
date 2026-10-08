package com.vintows.app.core.session

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.util.Base64
import javax.inject.Inject

/**
 * Claims in a Vintows access token, e.g.
 * `{ "userId":3, "role":"SuperAdmin", "email":"…", "type":"access", "scope":"admin",
 *    "tenantId":null, "iat":…, "exp":…, "aud":"veskill-client", "iss":"veskill-api" }`
 */
data class JwtClaims(
    val userId: String?,
    val role: String?,
    val email: String?,
    val scope: String?,
    val tenantId: String?,
    val issuedAt: Long?,
    val expiresAt: Long?,
)

/** Reads the payload only. The signature is verified by the server, not the app. */
class JwtDecoder @Inject constructor(private val json: Json) {

    fun decode(token: String): JwtClaims? {
        val payload = token.split('.').getOrNull(1) ?: return null
        val obj = try {
            val bytes = Base64.getUrlDecoder().decode(payload.padBase64())
            json.parseToJsonElement(bytes.decodeToString()).jsonObject
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: SerializationException) {
            return null
        }
        return JwtClaims(
            userId = obj.primitive("userId")?.contentOrNull,
            role = obj.primitive("role")?.contentOrNull,
            email = obj.primitive("email")?.contentOrNull,
            scope = obj.primitive("scope")?.contentOrNull,
            tenantId = obj.primitive("tenantId")?.contentOrNull,
            issuedAt = obj.primitive("iat")?.longOrNull,
            expiresAt = obj.primitive("exp")?.longOrNull,
        )
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun String.padBase64(): String = this + "=".repeat((4 - length % 4) % 4)
}
