package com.vintows.app.feature.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.ErrorKind
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.rbac.MenuApi
import com.vintows.app.core.rbac.MenuRepository
import com.vintows.app.core.session.Clock
import com.vintows.app.core.session.JwtDecoder
import com.vintows.app.core.session.SessionManager
import com.vintows.app.feature.auth.data.AuthApi
import com.vintows.app.feature.auth.data.AuthRepositoryImpl
import com.vintows.app.testing.FakeTokenCipher
import com.vintows.app.testing.fakeJwt
import com.vintows.app.testing.InMemoryPreferencesDataStore
import com.vintows.app.testing.testJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

class AuthRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: AuthRepositoryImpl

    private val accessToken = fakeJwt(
        """{"userId":3,"role":"SuperAdmin","email":"admin@example.com","type":"access","scope":"admin",
           "tenantId":null,"iat":1791381107,"exp":1791985907,"aud":"veskill-client","iss":"veskill-api"}""",
    )

    // Shape of the real QA auth/login response (tokens replaced).
    private val loginBody = """
        {"success":true,"statusCode":200,"message":"Login successful","data":{
          "accessToken":"$accessToken","refreshToken":"refresh-abc",
          "user":{"id":3,"email":"admin@example.com","mobileNo":"","roleId":1,"profilePicture":null},
          "expiresIn":"7d","newUser":false,"scope":"admin","tenantId":null,"dbName":null,
          "institutionId":null,"companyId":null,"coachingId":null,"trainerId":null,"entityId":null},
         "timestamp":"2026-10-07T13:51:47.160Z"}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
            .build()
        val apiCaller = ApiCaller(testJson)
        dataStore = InMemoryPreferencesDataStore()
        sessionManager = SessionManager(
            dataStore = dataStore,
            cipher = FakeTokenCipher(),
            appScope = CoroutineScope(Dispatchers.Unconfined),
            clock = Clock { 1_791_400_000 },
        )
        repository = AuthRepositoryImpl(
            api = retrofit.create<AuthApi>(),
            apiCaller = apiCaller,
            jwtDecoder = JwtDecoder(testJson),
            sessionManager = sessionManager,
            menuRepository = MenuRepository(retrofit.create<MenuApi>(), apiCaller),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login sends email twice and builds the session from response and JWT`() = runTest {
        server.enqueue(MockResponse().setBody(loginBody))

        val result = repository.login("  admin@example.com ", "Secret@123")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/login", request.path)
        val body = testJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("admin@example.com", body["email"]!!.jsonPrimitive.content)
        assertEquals("admin@example.com", body["mailId"]!!.jsonPrimitive.content)
        assertEquals("Secret@123", body["password"]!!.jsonPrimitive.content)

        val session = (result as NetworkResult.Success).data
        assertEquals(3, session.userId)
        assertEquals(1, session.roleId)
        assertEquals("SuperAdmin", session.role)
        assertEquals("admin", session.scope)
        assertNull(session.tenantId)
        assertEquals(1791985907L, session.expiresAtEpochSeconds)
        assertEquals("refresh-abc", session.refreshToken)
        assertEquals(session, sessionManager.current())
    }

    @Test
    fun `saved session survives an app restart`() = runTest {
        server.enqueue(MockResponse().setBody(loginBody))
        repository.login("admin@example.com", "Secret@123")

        // Fresh manager over the same file = cold start.
        val restarted = SessionManager(
            dataStore = dataStore,
            cipher = FakeTokenCipher(),
            appScope = CoroutineScope(Dispatchers.Unconfined),
            clock = Clock { 1_791_400_000 },
        )
        val restored = restarted.restore()

        assertEquals(accessToken, restored?.accessToken)
        assertEquals("SuperAdmin", restored?.role)
    }

    @Test
    fun `numeric tenantId from login becomes the x-tenant-id value`() = runTest {
        server.enqueue(MockResponse().setBody(loginBody.replace("\"tenantId\":null", "\"tenantId\":12")))

        val session = (repository.login("a@b.com", "secret1") as NetworkResult.Success).data

        assertEquals("12", session.tenantId)
    }

    @Test
    fun `failed login returns the server message and saves nothing`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"success":false,"error":{"message":"Invalid email or password","code":"AUTHENTICATION_ERROR","statusCode":401}}"""),
        )

        val result = repository.login("admin@example.com", "wrong-pass") as NetworkResult.Error

        assertEquals(ErrorKind.Unauthorized, result.kind)
        assertEquals("Invalid email or password", result.message)
        assertNull(sessionManager.current())
    }

    @Test
    fun `logout clears the session`() = runTest {
        server.enqueue(MockResponse().setBody(loginBody))
        repository.login("admin@example.com", "Secret@123")

        repository.logout()

        assertNull(sessionManager.current())
        assertTrue(sessionManager.restore() == null)
    }
}
