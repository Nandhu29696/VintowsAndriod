package com.vintows.app.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a Retrofit call and turns every outcome into a [NetworkResult].
 * Repositories call this instead of catching exceptions themselves.
 */
@Singleton
class ApiCaller @Inject constructor(private val json: Json) {

    /** For endpoints whose `data` is required (lists, details, login). */
    suspend fun <T : Any> call(block: suspend () -> ApiEnvelope<T>): NetworkResult<T> =
        execute(block) { envelope ->
            envelope.data?.let { NetworkResult.Success(it) }
                ?: NetworkResult.Error(ErrorKind.Unknown, envelope.errorMessage ?: EMPTY_RESPONSE)
        }

    /** For commands (PATCH/POST/DELETE) where `data` is irrelevant. */
    suspend fun callUnit(block: suspend () -> ApiEnvelope<JsonElement>): NetworkResult<Unit> =
        execute(block) { NetworkResult.Success(Unit) }

    private suspend fun <E, T> execute(
        block: suspend () -> ApiEnvelope<E>,
        onSuccess: (ApiEnvelope<E>) -> NetworkResult<T>,
    ): NetworkResult<T> = try {
        val envelope = block()
        if (envelope.success) {
            onSuccess(envelope)
        } else {
            NetworkResult.Error(ErrorKind.Client, envelope.errorMessage ?: GENERIC_ERROR, envelope.statusCode)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        httpError(e)
    } catch (e: IOException) {
        NetworkResult.Error(ErrorKind.Network, NETWORK_ERROR)
    } catch (e: SerializationException) {
        NetworkResult.Error(ErrorKind.Unknown, GENERIC_ERROR)
    } catch (e: IllegalArgumentException) {
        NetworkResult.Error(ErrorKind.Unknown, GENERIC_ERROR)
    }

    private fun httpError(e: HttpException): NetworkResult.Error {
        val code = e.code()
        val serverMessage = e.response()?.errorBody()?.string()?.let(::parseMessage)
        val kind = when {
            code == 401 -> ErrorKind.Unauthorized
            code == 403 -> ErrorKind.Forbidden
            code == 404 -> ErrorKind.NotFound
            code in 400..499 -> ErrorKind.Client
            code >= 500 -> ErrorKind.Server
            else -> ErrorKind.Unknown
        }
        val fallback = when (kind) {
            ErrorKind.Unauthorized -> "Your session has expired. Please log in again."
            ErrorKind.Forbidden -> "You don't have permission to do this."
            ErrorKind.NotFound -> "Not found."
            ErrorKind.Server -> "Server error. Please try again later."
            else -> GENERIC_ERROR
        }
        return NetworkResult.Error(kind, serverMessage ?: fallback, code)
    }

    private fun parseMessage(body: String): String? = try {
        json.decodeFromString<ApiEnvelope<JsonElement>>(body).errorMessage
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Please try again."
        const val NETWORK_ERROR = "No internet connection. Check your network and try again."
        const val EMPTY_RESPONSE = "The server returned an empty response."
    }
}
