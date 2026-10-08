package com.vintows.app.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.PATCH

class ApiCallerTest {

    @Serializable
    data class Category(val id: Int, val name: String, val isActive: Boolean)

    interface TestApi {
        @GET("support/categories")
        suspend fun categories(): ApiEnvelope<List<Category>>

        @PATCH("support/tickets/1/status")
        suspend fun updateStatus(): ApiEnvelope<JsonElement>
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var server: MockWebServer
    private lateinit var api: TestApi
    private val caller = ApiCaller(json)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(code: Int, body: String) =
        server.enqueue(MockResponse().setResponseCode(code).setBody(body).addHeader("Content-Type", "application/json"))

    @Test
    fun `success envelope returns data`() = runTest {
        enqueue(
            200,
            """{"success":true,"statusCode":200,"message":"Success","data":[
               {"id":1,"primaryid":"309973fa-bd66-4ad4-abf8-3f672575c700","name":"Access & Login","isActive":true,
                "createdAt":"2026-07-31T12:11:17.799Z","updatedAt":"2026-07-31T12:11:17.799Z"}],
               "timestamp":"2026-10-07T13:52:10.130Z"}""",
        )

        val result = caller.call { api.categories() }

        assertTrue(result is NetworkResult.Success)
        assertEquals("Access & Login", (result as NetworkResult.Success).data.single().name)
    }

    @Test
    fun `401 maps to Unauthorized with nested server message`() = runTest {
        enqueue(
            401,
            """{"success":false,"error":{"message":"No authentication token provided",
               "code":"AUTHENTICATION_ERROR","statusCode":401,"timestamp":"2026-10-07T13:54:13.926Z"}}""",
        )

        val result = caller.call { api.categories() } as NetworkResult.Error

        assertEquals(ErrorKind.Unauthorized, result.kind)
        assertEquals("No authentication token provided", result.message)
        assertEquals(401, result.httpCode)
    }

    @Test
    fun `500 maps to Server and never exposes the stack trace`() = runTest {
        enqueue(
            500,
            """{"success":false,"statusCode":500,"message":"column \"dob\" does not exist","code":"INTERNAL_ERROR",
               "timestamp":"2026-10-07T13:57:03.206Z","stack":"Error\n    at Query.run (/var/www/vintows/...)"}""",
        )

        val result = caller.call { api.categories() } as NetworkResult.Error

        assertEquals(ErrorKind.Server, result.kind)
        assertEquals("column \"dob\" does not exist", result.message)
        assertFalse(result.message.contains("/var/www"))
    }

    @Test
    fun `non-json error body falls back to a friendly message`() = runTest {
        enqueue(502, "<html>Bad Gateway</html>")

        val result = caller.call { api.categories() } as NetworkResult.Error

        assertEquals(ErrorKind.Server, result.kind)
        assertEquals("Server error. Please try again later.", result.message)
    }

    @Test
    fun `http 200 with success false is an error`() = runTest {
        enqueue(200, """{"success":false,"statusCode":409,"message":"typname already exists"}""")

        val result = caller.call { api.categories() } as NetworkResult.Error

        assertEquals(ErrorKind.Client, result.kind)
        assertEquals("typname already exists", result.message)
    }

    @Test
    fun `success without data is an error for call but fine for callUnit`() = runTest {
        enqueue(200, """{"success":true,"statusCode":200,"message":"Updated"}""")
        enqueue(200, """{"success":true,"statusCode":200,"message":"Updated"}""")

        assertTrue(caller.call { api.categories() } is NetworkResult.Error)
        assertEquals(NetworkResult.Success(Unit), caller.callUnit { api.updateStatus() })
    }

    @Test
    fun `connection failure maps to Network`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = caller.call { api.categories() } as NetworkResult.Error

        assertEquals(ErrorKind.Network, result.kind)
    }
}
