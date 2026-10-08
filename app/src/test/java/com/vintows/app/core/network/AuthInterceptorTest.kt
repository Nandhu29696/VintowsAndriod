package com.vintows.app.core.network

import com.vintows.app.core.session.Session
import com.vintows.app.core.session.SessionProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private class FakeSessionProvider(var session: Session?) : SessionProvider {
        var unauthorizedCalls = 0
        override fun current() = session
        override fun onUnauthorized() {
            unauthorizedCalls++
        }
    }

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun execute(provider: SessionProvider, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody("{}"))
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(provider)).build()
        client.newCall(Request.Builder().url(server.url("/api/v1/support/tickets")).build()).execute().close()
    }

    private val session = Session(
        accessToken = "token-123",
        userId = "3",
        email = "admin@example.com",
        roleId = 1,
        role = "SuperAdmin",
        scope = "admin",
        tenantId = "7",
    )

    @Test
    fun `adds bearer, tenant and scope headers when logged in`() {
        execute(FakeSessionProvider(session))

        val request = server.takeRequest()
        assertEquals("Bearer token-123", request.getHeader("Authorization"))
        assertEquals("7", request.getHeader("x-tenant-id"))
        assertEquals("admin", request.getHeader("x-db-scope"))
    }

    @Test
    fun `omits tenant header when user has no tenant`() {
        execute(FakeSessionProvider(session.copy(tenantId = null)))

        assertNull(server.takeRequest().getHeader("x-tenant-id"))
    }

    @Test
    fun `sends no auth headers when logged out`() {
        execute(FakeSessionProvider(null))

        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("x-db-scope"))
    }

    @Test
    fun `401 with a session triggers logout`() {
        val provider = FakeSessionProvider(session)
        execute(provider, code = 401)

        assertEquals(1, provider.unauthorizedCalls)
    }

    @Test
    fun `401 without a session does not trigger logout`() {
        val provider = FakeSessionProvider(null)
        execute(provider, code = 401)

        assertEquals(0, provider.unauthorizedCalls)
    }
}
