package com.vintows.app.core.network

import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET

/**
 * Read-only connectivity check used by the Phase 0 foundation screen.
 * Without a token the server answers 401 "No authentication token provided",
 * which proves the API is reachable and the error envelope parses.
 */
interface HealthApi {
    @GET("support/categories")
    suspend fun supportCategories(): ApiEnvelope<JsonElement>
}
