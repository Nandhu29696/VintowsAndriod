package com.vintows.app.core.network

import com.vintows.app.core.session.SessionProvider
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Adds the headers the Vintows API expects (same as the web app's interceptor):
 *  - `Authorization: Bearer <accessToken>`
 *  - `x-tenant-id`  when the user belongs to a tenant
 *  - `x-db-scope`   admin | global | licensed
 *
 * A 401 on an authenticated request means the token is no longer valid, so the
 * session is cleared and the app returns to Login.
 */
class AuthInterceptor @Inject constructor(
    private val sessionProvider: SessionProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val session = sessionProvider.current()
        val request = chain.request().newBuilder().apply {
            session?.accessToken?.let { header(HEADER_AUTHORIZATION, "Bearer $it") }
            session?.tenantId?.let { header(HEADER_TENANT, it) }
            session?.scope?.let { header(HEADER_SCOPE, it) }
        }.build()

        val response = chain.proceed(request)
        if (response.code == 401 && session != null) {
            sessionProvider.onUnauthorized()
        }
        return response
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_TENANT = "x-tenant-id"
        const val HEADER_SCOPE = "x-db-scope"
    }
}
