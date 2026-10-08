package com.vintows.app.core.network

import kotlinx.serialization.Serializable

/**
 * Every Vintows endpoint wraps its payload in this envelope.
 *
 * Success: `{ "success": true, "statusCode": 200, "message": "...", "data": {...}, "timestamp": "..." }`
 * Errors come in two shapes:
 *  - `{ "success": false, "error": { "message", "code", "statusCode" } }`        (e.g. 401)
 *  - `{ "success": false, "statusCode": 500, "message", "code", "stack" }`       (e.g. 500)
 * `stack` is deliberately not modelled so it can never reach the UI.
 */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = false,
    val statusCode: Int? = null,
    val message: String? = null,
    val data: T? = null,
    val code: String? = null,
    val error: ApiErrorBody? = null,
    val timestamp: String? = null,
) {
    /** The most specific human-readable message the server sent, if any. */
    val errorMessage: String?
        get() = error?.message?.takeIf { it.isNotBlank() } ?: message?.takeIf { it.isNotBlank() }
}

@Serializable
data class ApiErrorBody(
    val message: String? = null,
    val code: String? = null,
    val statusCode: Int? = null,
)
