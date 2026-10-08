package com.vintows.app.core.session

/**
 * Everything the app remembers about the logged-in user.
 * Mirrors the keys the web app keeps in localStorage after `auth/login`
 * (access_token, r6_userId, roleId, r6_role, r6_scope, r6_tenantId, r6_dbName).
 */
data class Session(
    val accessToken: String,
    val refreshToken: String? = null,
    /** Numeric for admins/trainers ("3"), UUID for learners. */
    val userId: String,
    val email: String,
    val roleId: Int? = null,
    val role: String? = null,
    /** `admin`, `global` or `licensed`. Sent as the `x-db-scope` header. */
    val scope: String? = null,
    /** Sent as the `x-tenant-id` header when present. */
    val tenantId: String? = null,
    val dbName: String? = null,
    /** JWT `exp`, seconds since epoch. */
    val expiresAtEpochSeconds: Long? = null,
) {
    fun isExpired(nowEpochSeconds: Long): Boolean =
        expiresAtEpochSeconds != null && nowEpochSeconds >= expiresAtEpochSeconds

    val isAdmin: Boolean get() = scope == "admin"
}

/** What the network layer needs from the session, kept small so it is easy to fake in tests. */
interface SessionProvider {
    fun current(): Session?
    fun onUnauthorized()
}

sealed interface SessionEvent {
    /** The server rejected the token; UI should navigate to Login. */
    data object Expired : SessionEvent
}
