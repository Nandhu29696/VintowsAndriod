package com.vintows.app.feature.notifications.data

import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.network.map
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationPage(
    val items: List<AppNotification>,
    val page: Int,
    val hasNextPage: Boolean,
)

@Singleton
class NotificationsRepository @Inject constructor(
    private val api: NotificationsApi,
    private val apiCaller: ApiCaller,
) {
    suspend fun page(page: Int): NetworkResult<NotificationPage> =
        apiCaller.call { api.list(page = page) }.map { dto ->
            NotificationPage(
                items = dto.items.mapIndexed { index, item -> item.toAppNotification(fallbackId = (page - 1) * dto.limit + index) },
                page = dto.page,
                hasNextPage = dto.hasNextPage,
            )
        }
}
