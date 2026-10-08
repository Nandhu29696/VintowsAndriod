package com.vintows.app.feature.auth.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ---------- Password login (licensed / admin users) ----------

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
 * returns numbers for some users/tenants and UUID strings for others (learners).
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
    val id: JsonElement? = null,
    val email: String? = null,
    val fullName: String? = null,
    val mobileNo: String? = null,
    val roleId: Int? = null,
    val role: String? = null,
    val profilePicture: String? = null,
)

// ---------- Student email-code sign-in (same flow as the web /glogin) ----------

@Serializable
data class SendCodeRequest(val email: String, val name: String)

@Serializable
data class VerifyCodeRequest(val email: String, val code: String)

/**
 * `data` of `emailverification/verify`. For an existing account `action == "login"` and the
 * session fields are present; otherwise the email is verified and the user must register.
 */
@Serializable
data class VerifyCodeData(
    val success: Boolean? = null,
    val message: String? = null,
    val action: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val role: String? = null,
    val user: LoginUserDto? = null,
    val scope: String? = null,
    val tenantId: JsonElement? = null,
    val dbName: String? = null,
)

/**
 * Body of `auth/register` for a learner: the web /register form fields, minus `dob`
 * (the web sends it, but `g_learners` has no dob column, so we never send it).
 * [role] and [googleSignedUp] have no defaults on purpose: fields equal to their default are not encoded.
 */
@Serializable
data class RegisterLearnerRequest(
    val email: String,
    val fullName: String,
    val mobileNo: String,
    val currentRole: String,
    val experienceYears: String,
    val techSkills: List<String>,
    val softSkills: List<String>,
    val targetRole: String,
    val upskillInterest: List<String>,
    val placementReady: String,
    val weeklyHours: String,
    val notes: String,
    val role: String,
    val googleSignedUp: Boolean,
) {
    companion object {
        const val ROLE_LEARNER = "learner"
    }
}

@Serializable
data class RegisterData(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: LoginUserDto? = null,
    val scope: String? = null,
    val tenantId: JsonElement? = null,
)

/** `12`, `"12"` and `"uuid"` become strings; `null` and objects become null. */
internal fun JsonElement?.asIdString(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
