package com.vintows.app.feature.assessments

import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.session.Session
import com.vintows.app.core.session.SessionProvider
import com.vintows.app.feature.assessments.data.AssessmentsApi
import com.vintows.app.feature.assessments.data.AssessmentsRepositoryImpl
import com.vintows.app.feature.assessments.domain.AnswerValue
import com.vintows.app.feature.assessments.domain.QuestionKind
import com.vintows.app.feature.courses.data.CoursesApi
import com.vintows.app.feature.courses.data.CoursesRepositoryImpl
import com.vintows.app.testing.resource
import com.vintows.app.testing.testJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.net.URLDecoder
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/** Assessment and test-attempt calls through the full Retrofit stack, with shapes from QA and the web player. */
class AssessmentsRepositoryImplTest {

    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<Pair<String, String>>() // path to body
    private val routes = mutableMapOf<String, Pair<Int, String>>()
    private var session: Session? = Session(accessToken = "t", userId = "u-1", email = "s@example.com", role = "Learner", scope = "global")
    private lateinit var repository: AssessmentsRepositoryImpl

    // One of the two real QA assessments (settings as stored on QA, status changed to PUBLISHED).
    private val qaAssessment = """{"id":"c64b8cdb-2f2e-407a-9c6e-1f485949e7e3","name":"Unit test","type":"TEST","level_code":"SUBJECT",
        "record_id":"s-2","settings":{"passMark":4,"proctored":true,"maxAttempts":2,"timeLimitMinutes":30,"negativeMarking":false},
        "is_active":true,"content_status":"PUBLISHED"}"""

    // Start response in the shape the web player reads (t.data.id, questions[].type/text/options, timeLimitMinutes…).
    private val started = """{"success":true,"data":{"id":"att-1","timeLimitMinutes":20,"maxAttempts":2,"attemptsRemaining":1,
        "proctored":true,"startedAt":"2026-10-10T10:00:00Z","questions":[
          {"id":101,"type":"SCQ","text":"2 + 2 = ?","options":["3","4","5"],"marks":1},
          {"id":"q-uuid","type":"MCQ","text":"Pick primes","options":["2","4","5"]},
          {"id":103,"type":"TRUE_FALSE","text":"The sky is green"},
          {"id":104,"type":"FILL_BLANK","text":"Paris is the capital of ___ and Rome of ___."},
          {"id":105,"type":"SHORT_ANSWER","text":"Name a sorting algorithm"},
          {"id":106,"type":"LONG_ANSWER","text":"Explain recursion"},
          {"id":107,"type":"SCQ","text":"Skipped one","options":["a","b"]}
        ]}}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = URLDecoder.decode(request.path!!.removePrefix("/api/v1/"), "UTF-8")
                requests += path to request.body.readUtf8()
                val (code, body) = routes[path] ?: routes[path.substringBefore('?')] ?: (200 to """{"success":true,"data":[]}""")
                return MockResponse().setResponseCode(code).setBody(body)
            }
        }
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
            .build()
        val sessions = object : SessionProvider {
            override fun current() = session
            override fun onUnauthorized() = Unit
        }
        val apiCaller = ApiCaller(testJson)
        val coursesApi = retrofit.create<CoursesApi>()
        repository = AssessmentsRepositoryImpl(
            api = retrofit.create<AssessmentsApi>(),
            coursesApi = coursesApi,
            courses = CoursesRepositoryImpl(coursesApi, apiCaller, sessions),
            apiCaller = apiCaller,
            sessions = sessions,
        )
        routes["course-builder/level-config"] = 200 to resource("courses/level_config.json")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `my tests are gathered level by level, only where counts are above zero`() = runTest {
        routes["course-builder/nodes?tableName=subjects"] = 200 to
            """{"success":true,"data":[{"id":"s-1","name":"Algebra"},{"id":"s-2","name":"Geometry"}]}"""
        routes["course-builder/assessments/counts?levelCode=SUBJECT&recordIds=s-1,s-2"] = 200 to
            """{"success":true,"data":{"s-1":0,"s-2":1}}"""
        routes["course-builder/assessments/for-student?levelCode=SUBJECT&recordId=s-2"] = 200 to """{"success":true,"data":[$qaAssessment]}"""

        val tests = (repository.myTests() as NetworkResult.Success).data

        val test = tests.single()
        assertEquals("Unit test", test.name)
        assertEquals("Geometry", test.nodeName)
        assertEquals(30, test.timeLimitMinutes)
        assertEquals(2, test.maxAttempts)
        assertEquals(4.0, test.passMark)
        assertTrue(test.proctored)
        assertFalse(test.isDraft)
        // No tests asked for at s-1, and levels without nodes never call counts.
        assertTrue(requests.none { it.first.contains("recordId=s-1") })
        assertEquals(1, requests.count { it.first.startsWith("course-builder/assessments/counts") })
    }

    @Test
    fun `admins list tests with drafts through the staff endpoint`() = runTest {
        session = Session(accessToken = "t", userId = "3", email = "a@example.com", role = "SuperAdmin", scope = "admin")

        repository.testsForNode("SUBJECT", "s-2")

        assertTrue(requests.any { it.first == "course-builder/assessments?levelCode=SUBJECT&recordId=s-2" })
        assertTrue(requests.none { it.first.contains("for-student") })
    }

    @Test
    fun `check returns attempts left, and the server's reason when a test can't start`() = runTest {
        routes["test-attempts/check?assessmentId=a1"] = 200 to """{"success":true,"data":{"canStart":false,"maxAttempts":1,"attemptsRemaining":0}}"""
        routes["test-attempts/check?assessmentId=a2"] = 400 to
            """{"success":false,"error":{"message":"This test has no questions yet.","code":"VALIDATION_ERROR","statusCode":400}}"""

        val check = (repository.check("a1") as NetworkResult.Success).data
        assertFalse(check.canStart)
        assertEquals(0, check.attemptsRemaining)

        assertEquals("This test has no questions yet.", (repository.check("a2") as NetworkResult.Error).message)
    }

    @Test
    fun `start sends the assessment id and maps every question type`() = runTest {
        routes["test-attempts"] = 200 to started

        val attempt = (repository.start("a1") as NetworkResult.Success).data

        assertEquals("""{"assessmentId":"a1"}""", requests.single { it.first == "test-attempts" }.second)
        assertEquals("att-1", attempt.id)
        assertEquals(20, attempt.timeLimitMinutes)
        assertEquals(Instant.parse("2026-10-10T10:00:00Z").toEpochMilli(), attempt.startedAtMillis)
        assertTrue(attempt.proctored)
        assertEquals(
            listOf(
                QuestionKind.SingleChoice, QuestionKind.MultipleChoice, QuestionKind.TrueFalse, QuestionKind.FillBlank,
                QuestionKind.ShortText, QuestionKind.LongText, QuestionKind.SingleChoice,
            ),
            attempt.questions.map { it.kind },
        )
        assertEquals(listOf("3", "4", "5"), attempt.questions[0].options)
        assertEquals(2, attempt.questions[3].blankCount)
    }

    @Test
    fun `submit sends answers exactly like the web player`() = runTest {
        routes["test-attempts"] = 200 to started
        routes["test-attempts/att-1/submit"] = 200 to """{"success":true,"data":{"score":5,"maxScore":7,"passed":true}}"""
        val q = (repository.start("a1") as NetworkResult.Success).data.questions
        val answers = mapOf(
            q[0].key to AnswerValue.Choices(listOf(2)),
            q[1].key to AnswerValue.Choices(listOf(3, 1)),
            q[2].key to AnswerValue.Choices(listOf(2)),
            q[3].key to AnswerValue.Blanks(listOf(" France ", "Italy")),
            q[4].key to AnswerValue.Text("  Merge sort "),
            q[5].key to AnswerValue.Text("A function calling itself"),
        )

        val result = (repository.submit("att-1", q, answers) as NetworkResult.Success).data

        val body = testJson.parseToJsonElement(requests.single { it.first == "test-attempts/att-1/submit" }.second).jsonObject
        assertEquals(
            """[{"questionId":101,"answer":[2]},{"questionId":"q-uuid","answer":[1,3]},{"questionId":103,"answer":[2]},""" +
                """{"questionId":104,"answer":"France Italy"},{"questionId":105,"answer":"Merge sort"},""" +
                """{"questionId":106,"answer":"A function calling itself"},{"questionId":107,"answer":[]}]""",
            (body["answers"] as JsonArray).toString(),
        )
        assertEquals(5.0, result.score)
        assertEquals(7.0, result.maxScore)
        assertEquals(true, result.passed)
        assertEquals(5f / 7f, result.fraction!!, 0.001f)
    }

    @Test
    fun `result without a score waits for review`() = runTest {
        routes["test-attempts/att-1/submit"] = 200 to """{"success":true,"data":{"status":"submitted","needs_review":true}}"""

        val result = (repository.submit("att-1", emptyList(), emptyMap()) as NetworkResult.Success).data

        assertNull(result.score)
        assertTrue(result.needsReview)
        assertNull(result.fraction)
    }

    @Test
    fun `proctoring event uses the web's tab_switch format`() = runTest {
        repository.reportProctoringEvent("att-1", "tab_switch", "1/3")

        assertEquals("""{"type":"tab_switch","detail":"1/3"}""", requests.single { it.first == "test-attempts/att-1/proctoring-event" }.second)
    }
}
