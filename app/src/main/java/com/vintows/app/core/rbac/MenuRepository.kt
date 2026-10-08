package com.vintows.app.core.rbac

import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.network.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Loads the role's menu tree once per login and keeps it in memory. */
@Singleton
class MenuRepository @Inject constructor(
    private val api: MenuApi,
    private val apiCaller: ApiCaller,
) {
    private val mutex = Mutex()
    private var cached: Pair<Int, List<MenuItem>>? = null

    suspend fun menus(roleId: Int, forceRefresh: Boolean = false): NetworkResult<List<MenuItem>> = mutex.withLock {
        cached?.takeIf { !forceRefresh && it.first == roleId }?.let { return@withLock NetworkResult.Success(it.second) }

        apiCaller.call { api.roleAccess(roleId = roleId) }
            .map { it.toMenuItems() }
            .also { if (it is NetworkResult.Success) cached = roleId to it.data }
    }

    suspend fun clearCache() = mutex.withLock { cached = null }
}
