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
import com.vintows.app.feature.auth.domain.LearnerRegistration
import com.vintows.app.feature.auth.domain.VerifyOutcome
import com.vintows.app.testing.FakePushRegistrar
import com.vintows.app.testing.FakeTokenCipher
import com.vintows.app.testing.InMemoryPreferencesDataStore
import com.vintows.app.testing.fakeJwt
import com.vintows.app.testing.testJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

class AuthRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var sessionManager: SessionManager
    private lateinit var push: FakePushRegistrar
    private lateinit var repository: AuthRepositoryImpl

    private val adminToken = fakeJwt(
        """{"userId":3,"role":"SuperAdmin","email":"admin@example.com","type":"access","scope":"admin",
           "tenantId":null,"iat":1791381107,"exp":1791985907,"aud":"veskill-client","iss":"veskill-api"}""",
    )
    private val learnerId = "5b7c1e2a-9f3d-4c1b-8a6e-2d4f6a8b0c1d"
    private val learnerToken = fakeJwt(
        """{"userId":"$learnerId","role":"Learner","email":"student@example.com","type":"access","scope":"global","exp":1791985907}""",
    )

    // Shape of the real QA auth/login response (tokens replaced).
    private val loginBody = """
        {"success":true,"statusCode":200,"message":"Login successful","data":{
          "accessToken":"$adminToken","refreshToken":"refresh-abc",
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
        sessionManager = newSessionManager()
        push = FakePushRegistrar()
        repository = AuthRepositoryImpl(
            api = retrofit.create<AuthApi>(),
            apiCaller = apiCaller,
            jwtDecoder = JwtDecoder(testJson),
            sessionManager = sessionManager,
            menuRepository = MenuRepository(retrofit.create<MenuApi>(), apiCaller),
            push = push,
            appScope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    private fun newSessionManager() = SessionManager(
        dataStore = dataStore,
        cipher = FakeTokenCipher(),
        appScope = CoroutineScope(Dispatchers.Unconfined),
        clock = Clock { 1_791_400_000 },
    )

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun requestJson(): JsonObject = testJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    // ---------- password login ----------

    @Test
    fun `login sends email twice and builds the session from response and JWT`() = runTest {
        enqueue(loginBody)

        val result = repository.login("  admin@example.com ", "Secret@123")

        val request = server.takeRequest()
        assertEquals("/api/v1/auth/login", request.path)
        val body = testJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("admin@example.com", body["email"]!!.jsonPrimitive.content)
        assertEquals("admin@example.com", body["mailId"]!!.jsonPrimitive.content)
        assertEquals("Secret@123", body["password"]!!.jsonPrimitive.content)

        val session = (result as NetworkResult.Success).data
        assertEquals("3", session.userId)
        assertEquals(1, session.roleId)
        assertEquals("SuperAdmin", session.role)
        assertEquals("admin", session.scope)
        assertNull(session.tenantId)
        assertEquals(1791985907L, session.expiresAtEpochSeconds)
        assertEquals(session, sessionManager.current())
        assertEquals(1, push.registered)
    }

    @Test
    fun `saved session survives an app restart`() = runTest {
        enqueue(loginBody)
        repository.login("admin@example.com", "Secret@123")

        val restored = newSessionManager().restore()

        assertEquals(adminToken, restored?.accessToken)
        assertEquals("SuperAdmin", restored?.role)
    }

    @Test
    fun `numeric tenantId from login becomes the x-tenant-id value`() = runTest {
        enqueue(loginBody.replace("\"tenantId\":null", "\"tenantId\":12"))

        val session = (repository.login("a@b.com", "secret1") as NetworkResult.Success).data

        assertEquals("12", session.tenantId)
    }

    @Test
    fun `failed login returns the server message and saves nothing`() = runTest {
        enqueue("""{"success":false,"error":{"message":"Invalid email or password","code":"AUTHENTICATION_ERROR","statusCode":401}}""", 401)

        val result = repository.login("admin@example.com", "wrong-pass") as NetworkResult.Error

        assertEquals(ErrorKind.Unauthorized, result.kind)
        assertEquals("Invalid email or password", result.message)
        assertNull(sessionManager.current())
        assertEquals(0, push.registered)
    }

    @Test
    fun `logout unregisters push, then clears the session`() = runTest {
        enqueue(loginBody)
        repository.login("admin@example.com", "Secret@123")

        repository.logout()

        assertEquals(1, push.unregistered)
        assertNull(sessionManager.current())
        assertNull(newSessionManager().restore())
    }

    // ---------- student email-code flow ----------

    @Test
    fun `sendCode posts email and name like the web`() = runTest {
        enqueue("""{"success":true,"message":"Verification code sent"}""")

        val result = repository.sendCode(" student@example.com ", "Asha")

        assertEquals(NetworkResult.Success(Unit), result)
        val body = requestJson()
        assertEquals("student@example.com", body["email"]!!.jsonPrimitive.content)
        assertEquals("Asha", body["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `verify for an existing student logs in with a UUID user id`() = runTest {
        enqueue(
            """{"success":true,"data":{"success":true,"action":"login","accessToken":"$learnerToken","role":"Learner",
               "user":{"id":"$learnerId","email":"student@example.com"}}}""",
        )

        val outcome = (repository.verifyCode("student@example.com", "1234") as NetworkResult.Success).data

        val session = (outcome as VerifyOutcome.LoggedIn).session
        assertEquals(learnerId, session.userId)
        assertEquals("Learner", session.role)
        assertEquals("global", session.scope)
        assertEquals(session, sessionManager.current())
        assertEquals("1234", requestJson()["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `verify for a new email asks for registration and saves nothing`() = runTest {
        enqueue("""{"success":true,"message":"Email verified","data":{"success":true,"message":"Email verified"}}""")

        val outcome = (repository.verifyCode("new@example.com", "1234") as NetworkResult.Success).data

        assertEquals(VerifyOutcome.NeedsRegistration, outcome)
        assertNull(sessionManager.current())
    }

    @Test
    fun `wrong code reported inside data is an error`() = runTest {
        enqueue("""{"success":true,"data":{"success":false,"message":"Invalid verification code"}}""")

        val result = repository.verifyCode("student@example.com", "0000") as NetworkResult.Error

        assertEquals("Invalid verification code", result.message)
    }

    @Test
    fun `expired code as HTTP 410 is an error with the server message`() = runTest {
        enqueue("""{"success":false,"message":"Code expired"}""", 410)

        val result = repository.verifyCode("student@example.com", "1234") as NetworkResult.Error

        assertEquals("Code expired", result.message)
    }

    @Test
    fun `registerLearner sends the web form fields and starts a session`() = runTest {
        enqueue(
            """{"success":true,"data":{"accessToken":"$learnerToken","refreshToken":"r1",
               "user":{"id":"$learnerId","email":"student@example.com","role":"Learner"}}}""",
        )
        val form = LearnerRegistration(
            email = "student@example.com",
            fullName = " Asha K ",
            phone = "9876543210",
            currentRole = "jobseeker",
            experienceYears = "fresher",
            techSkills = listOf("Python", "SQL"),
            upskillInterest = listOf("Data Science"),
            placementReady = "3months",
            weeklyHours = "5-10",
        )

        val session = (repository.registerLearner(form) as NetworkResult.Success).data

        val body = requestJson()
        assertEquals("learner", body["role"]!!.jsonPrimitive.content)
        assertEquals("Asha K", body["fullName"]!!.jsonPrimitive.content)
        assertEquals("9876543210", body["mobileNo"]!!.jsonPrimitive.content)
        assertEquals("jobseeker", body["currentRole"]!!.jsonPrimitive.content)
        assertEquals(listOf("Python", "SQL"), body["techSkills"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("false", body["googleSignedUp"]!!.jsonPrimitive.content)
        assertFalse("dob is never sent: g_learners has no dob column", body.containsKey("dob"))
        assertEquals(learnerId, session.userId)
        assertEquals(session, sessionManager.current())
        assertEquals(1, push.registered)
    }

    @Test
    fun `registration of an existing email shows the conflict message`() = runTest {
        enqueue("""{"success":false,"message":"An account with this email already exists"}""", 409)

        val result = repository.registerLearner(LearnerRegistration(email = "s@example.com")) as NetworkResult.Error

        assertEquals("An account with this email already exists", result.message)
        assertNull(sessionManager.current())
    }
}
