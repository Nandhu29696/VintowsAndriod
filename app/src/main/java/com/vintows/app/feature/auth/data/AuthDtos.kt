package com.vintows.app.feature.auth.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The web app sends the email twice (`email` and `mailId`), so we do too.
 * No default for [mailId]: kotlinx.serialization omits fields equal to their default.
 */
@Serializable
data class LoginRequest(
    val email: String,
    val mailId: String,
    val password: String,
) {
    companion object {
        fun of(email: String, password: String) = LoginRequest(email = email, mailId = email, password = password)
    }
}

/**
 * `data` of a successful `auth/login`. Id fields are [JsonElement] because the server
 * returns numbers for some tenants and UUID strings for others.
 */
@Serializable
data class LoginData(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: String? = null,
    val user: LoginUserDto,
    val newUser: Boolean = false,
    val scope: String? = null,
    val tenantId: JsonElement? = null,
    val dbName: String? = null,
    val institutionId: JsonElement? = null,
    val companyId: JsonElement? = null,
    val coachingId: JsonElement? = null,
    val trainerId: JsonElement? = null,
    val entityId: JsonElement? = null,
)

@Serializable
data class LoginUserDto(
    val id: Int,
    val email: String,
    val mobileNo: String? = null,
    val roleId: Int? = null,
    val profilePicture: String? = null,
)

/** `12`, `"12"` and `"uuid"` become strings; `null` and objects become null. */
internal fun JsonElement?.asIdString(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
