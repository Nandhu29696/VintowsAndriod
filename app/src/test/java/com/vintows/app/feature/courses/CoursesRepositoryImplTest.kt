package com.vintows.app.feature.courses

import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.session.Session
import com.vintows.app.core.session.SessionProvider
import com.vintows.app.feature.courses.data.CoursesApi
import com.vintows.app.feature.courses.data.CoursesRepositoryImpl
import com.vintows.app.feature.courses.domain.ContentType
import com.vintows.app.feature.courses.domain.CourseHierarchy
import com.vintows.app.testing.resource
import com.vintows.app.testing.testJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.CopyOnWriteArrayList

/** Real QA course-builder responses (saved 2026-10-10) through the full Retrofit stack. */
class CoursesRepositoryImplTest {

    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<String>()

    /** path (without /api/v1/) → body; unknown paths answer `{"success":true,"data":[]}`. */
    private val routes = mutableMapOf<String, Pair<Int, String>>()

    private var session: Session? = learner()

    private fun learner() = Session(accessToken = "t", userId = "u-1", email = "s@example.com", role = "Learner", scope = "global")
    private fun admin() = Session(accessToken = "t", userId = "3", email = "a@example.com", role = "SuperAdmin", scope = "admin")

    private val sessions = object : SessionProvider {
        override fun current() = session
        override fun onUnauthorized() = Unit
    }

    private lateinit var repository: CoursesRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!.removePrefix("/api/v1/")
                requests += path
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
        repository = CoursesRepositoryImpl(retrofit.create<CoursesApi>(), ApiCaller(testJson), sessions)
        routes["course-builder/level-config"] = 200 to resource("courses/level_config.json")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `hierarchy skips the disabled Department and Semester levels`() = runTest {
        val h = (repository.hierarchy() as NetworkResult.Success).data

        assertEquals(listOf("PROGRAM", "SUBJECT", "CHAPTER", "LESSON", "TOPIC"), h.levels.map { it.code })
        assertEquals("SUBJECT", h.childOf("PROGRAM")?.code)
        assertNull(h.childOf("TOPIC"))
        assertEquals("program_id", CourseHierarchy.foreignKeyOf(h.level("PROGRAM")!!))
        assertEquals("Chapter / Unit", h.level("CHAPTER")!!.label)
    }

    @Test
    fun `level config is fetched once`() = runTest {
        repository.hierarchy()
        repository.hierarchy()

        assertEquals(1, requests.count { it == "course-builder/level-config" })
    }

    @Test
    fun `programs from QA, price as text`() = runTest {
        routes["course-builder/nodes?tableName=programs"] = 200 to resource("courses/programs.json")

        val programs = (repository.programs() as NetworkResult.Success).data

        assertEquals(6, programs.size)
        assertTrue(programs.all { it.levelCode == "PROGRAM" && !it.isPaid && it.price == "0.00" })
        assertTrue("course-builder/nodes?tableName=programs" in requests)
    }

    @Test
    fun `program screen asks for subjects by program_id and collects its materials`() = runTest {
        session = admin() // QA content is all drafts; admins see them
        val programId = "110ed6ce-7296-4920-aaaa-711e43e0b25b"
        routes["course-builder/nodes?tableName=subjects&parentFk=program_id&parentId=$programId"] = 200 to resource("courses/subjects.json")
        routes["course-builder/content/live-docs?levelCode=PROGRAM&recordId=$programId"] = 200 to resource("courses/live_docs.json")

        val contents = (repository.nodeContents("PROGRAM", programId, programId) as NetworkResult.Success).data

        assertEquals("SUBJECT", contents.childLevel?.code)
        assertEquals(listOf("testing", "Test document"), contents.children.map { it.name })
        assertEquals(ContentType.LiveDoc, contents.materials.single().type)
        assertTrue(contents.materials.single().isDraft)
        // One request per content type, all scoped to this node.
        assertEquals(ContentType.entries.size, requests.count { it.startsWith("course-builder/content/") && it.contains("recordId=$programId") })
    }

    @Test
    fun `learners never see draft materials`() = runTest {
        session = learner()
        routes["course-builder/content/live-docs?levelCode=SUBJECT&recordId=s1"] = 200 to resource("courses/live_docs.json")
        routes["course-builder/content/smart-links?levelCode=SUBJECT&recordId=s1"] = 200 to
            """{"success":true,"data":[{"id":"l1","name":"Docs","url":"https://example.com","content_status":"PUBLISHED","is_active":true}]}"""

        val contents = (repository.nodeContents("SUBJECT", "s1", "p1") as NetworkResult.Success).data

        assertEquals(listOf("Docs"), contents.materials.map { it.name })
        assertEquals("https://example.com", contents.materials.single().url)
    }

    @Test
    fun `lessons list their topics through topics by-node`() = runTest {
        routes["course-builder/topics/by-node?levelCode=LESSON&recordId=l1&programId=p1"] = 200 to
            """{"success":true,"data":[
                 {"id":"t2","name":"Loops","sequence_number":2,"duration_value":30,"is_active":true},
                 {"id":"t1","name":"Variables","sequence_number":1,"content_url":"https://example.com/v","is_paid":true,"price":"199.00"},
                 {"id":"t3","name":"Old","is_active":false}
               ]}"""

        val contents = (repository.nodeContents("LESSON", "l1", "p1") as NetworkResult.Success).data

        assertEquals("TOPIC", contents.childLevel?.code)
        assertEquals(listOf("Variables", "Loops"), contents.children.map { it.name })
        assertEquals("30 min", contents.children[1].duration)
        assertEquals("https://example.com/v", contents.children[0].contentUrl)
        assertTrue(contents.children[0].isPaid)
    }

    @Test
    fun `topic is the bottom level and asks for no children`() = runTest {
        val contents = (repository.nodeContents("TOPIC", "t1", "p1") as NetworkResult.Success).data

        assertNull(contents.childLevel)
        assertTrue(contents.children.isEmpty())
        assertTrue(requests.none { it.startsWith("course-builder/nodes") || it.startsWith("course-builder/topics") })
    }

    @Test
    fun `a failing content type doesn't hide the rest, a failing child list is an error`() = runTest {
        routes["course-builder/content/whiteboards"] = 500 to """{"success":false,"message":"boom"}"""
        routes["course-builder/content/smart-links?levelCode=SUBJECT&recordId=s1"] = 200 to
            """{"success":true,"data":[{"id":"l1","name":"Docs","content_status":"PUBLISHED"}]}"""

        val ok = (repository.nodeContents("SUBJECT", "s1", "p1") as NetworkResult.Success).data
        assertEquals(listOf("Docs"), ok.materials.map { it.name })

        routes["course-builder/nodes"] = 500 to """{"success":false,"message":"boom"}"""
        assertTrue(repository.nodeContents("SUBJECT", "s1", "p1") is NetworkResult.Error)
    }

    @Test
    fun `content item is found by id, drafts hidden from learners`() = runTest {
        routes["course-builder/content/live-docs?levelCode=SUBJECT&recordId=02d3dd42-edc9-4192-a07e-1f18b07c4702"] = 200 to resource("courses/live_docs.json")
        val id = "a616de11-4b26-471c-bfe1-a5f5276a44e5"

        session = learner()
        assertTrue(repository.contentItem(ContentType.LiveDoc, id, "SUBJECT", "02d3dd42-edc9-4192-a07e-1f18b07c4702") is NetworkResult.Error)

        session = admin()
        val doc = (repository.contentItem(ContentType.LiveDoc, id, "SUBJECT", "02d3dd42-edc9-4192-a07e-1f18b07c4702") as NetworkResult.Success).data
        assertTrue(doc.html!!.contains("Ask Vintows"))
    }
}
