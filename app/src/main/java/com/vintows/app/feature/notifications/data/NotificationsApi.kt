package com.vintows.app.feature.notifications.data

import com.vintows.app.core.network.ApiEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface NotificationsApi {
    /** `{ items, total, page, limit, totalPages, hasNextPage, hasPreviousPage }` */
    @GET("notifications")
    suspend fun list(
        @Query("page") page: Int,
        @Query("limit") limit: Int = PAGE_SIZE,
    ): ApiEnvelope<NotificationPageDto>

    companion object {
        const val PAGE_SIZE = 20
    }
}

/**
 * Items are kept as raw JSON: the QA list is empty, so the item shape hasn't been observed yet.
 * [toAppNotification] accepts the field names used by the `notifications` / `org_notifications` tables.
 */
@Serializable
data class NotificationPageDto(
    val items: List<JsonObject> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = NotificationsApi.PAGE_SIZE,
    val totalPages: Int = 0,
    val hasNextPage: Boolean = false,
)
