package com.vintows.app.core.network

/**
 * Masks secrets in HTTP debug logs. Without this, logcat shows the login password
 * and the access/refresh tokens in plain text.
 */
object LogRedactor {
    private val SENSITIVE_JSON_FIELD =
        Regex("\"(password|accessToken|refreshToken|token)\"\\s*:\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE)
    private val BEARER = Regex("Bearer\\s+[A-Za-z0-9._~+/=-]+")

    fun redact(message: String): String =
        message
            .replace(SENSITIVE_JSON_FIELD) { "\"${it.groupValues[1]}\":\"██\"" }
            .replace(BEARER, "Bearer ██")
}
