package com.vintows.app.core.network

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>

    data class Error(
        val kind: ErrorKind,
        val message: String,
        val httpCode: Int? = null,
    ) : NetworkResult<Nothing>
}

enum class ErrorKind {
    /** No connection, timeout, DNS failure. */
    Network,

    /** 401: token missing, expired or rejected. */
    Unauthorized,

    /** 403: logged in but not allowed. */
    Forbidden,

    /** 404 */
    NotFound,

    /** Other 4xx, or HTTP 200 with `success: false`. */
    Client,

    /** 5xx */
    Server,

    /** Malformed JSON or anything unexpected. */
    Unknown,
}

inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(data))
    is NetworkResult.Error -> this
}
