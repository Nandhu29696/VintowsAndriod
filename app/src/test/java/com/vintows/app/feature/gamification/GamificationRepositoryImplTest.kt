package com.vintows.app.feature.gamification

import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.ErrorKind
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.feature.gamification.data.GamificationApi
import com.vintows.app.feature.gamification.data.GamificationRepositoryImpl
import com.vintows.app.feature.gamification.domain.GoalStatus
import com.vintows.app.testing.resource
import com.vintows.app.testing.testJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

/** Parses the real QA responses (saved 2026-10-09 in test resources) through the full Retrofit stack. */
class GamificationRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: GamificationRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = GamificationRepositoryImpl(retrofit.create<GamificationApi>(), ApiCaller(testJson))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun fixture(name: String) = enqueue(resource("gamification/$name.json"))

    @Test
    fun `progression from QA`() = runTest {
        fixture("progression")

        val p = (repository.progression() as NetworkResult.Success).data

        assertEquals("/api/v1/gamification/progression", server.takeRequest().path)
        assertEquals(1, p.level)
        assertEquals("Beginner", p.levelName)
        assertEquals(100L, p.nextLevelXp)
        assertEquals("Learner", p.nextLevelName)
        assertEquals(0f, p.progress)
    }

    @Test
    fun `streaks from QA, with the next milestone`() = runTest {
        fixture("streak")

        val streaks = (repository.streaks() as NetworkResult.Success).data

        assertEquals(listOf("Daily Learning", "Weekly Quiz"), streaks.map { it.title })
        assertEquals(listOf("day", "week"), streaks.map { it.unit })
        assertEquals(3, streaks[0].nextMilestone?.count)
        assertEquals(25, streaks[0].nextMilestone?.rewardPoints)
        assertEquals(4, streaks[1].nextMilestone?.count)
    }

    @Test
    fun `missions from QA`() = runTest {
        fixture("missions")

        val mission = (repository.missions() as NetworkResult.Success).data.single()

        assertEquals("Complete Your First Week", mission.name)
        assertEquals("Weekly", mission.period)
        assertEquals(GoalStatus.NotStarted, mission.status)
        assertEquals(listOf("Complete 3 lessons", "Complete 1 quiz"), mission.objectives.map { it.name })
        assertEquals(0f, mission.fraction)
    }

    @Test
    fun `badges from QA, earned ones first`() = runTest {
        // QA has no earned badges yet, so mark the last one (Course Finisher) as earned.
        val earnedLast = resource("gamification/badges.json").replace(
            "\"icon\":\"bi-trophy\",\"rewardPoints\":100,\"rewardXp\":150,\"isRepeatable\":false,\"earned\":false,\"earnedAt\":null",
            "\"icon\":\"bi-trophy\",\"rewardPoints\":100,\"rewardXp\":150,\"isRepeatable\":false,\"earned\":true,\"earnedAt\":\"2026-10-01T10:00:00Z\"",
        )
        enqueue(earnedLast)

        val badges = (repository.badges() as NetworkResult.Success).data

        assertEquals(4, badges.size)
        assertEquals("Course Finisher", badges.first().name)
        assertTrue(badges.first().earned)
        assertEquals("bi-trophy", badges.first().icon)
        assertFalse(badges.drop(1).any { it.earned })
    }

    @Test
    fun `achievements from QA`() = runTest {
        fixture("achievements")

        val list = (repository.achievements() as NetworkResult.Success).data

        assertEquals(10, list.size)
        assertEquals("First Course", list.first().name)
        assertTrue(list.all { it.status == GoalStatus.NotStarted })
        assertEquals(1000, list.first { it.name == "Earn 1000 Points" }.target)
    }

    @Test
    fun `leaderboard filters keep only switched-on boards and periods, in admin order`() = runTest {
        fixture("leaderboard_settings")

        val filters = (repository.leaderboardFilters() as NetworkResult.Success).data

        assertEquals("/api/v1/gamification/leaderboard/settings?category=types", server.takeRequest().path)
        assertEquals(
            listOf("Overall", "Quiz", "Assignment", "Coding", "Project", "Course Completion", "Learning Streak"),
            filters.types.map { it.label },
        )
        assertEquals(listOf("all_time", "daily", "weekly", "monthly"), filters.periods.map { it.key })
        assertEquals("All time", filters.periods.first().label)
    }

    @Test
    fun `no switched-on boards falls back to Overall`() = runTest {
        enqueue("""{"success":true,"data":[{"setting_key":"typeQuiz","setting_name":"Quiz","setting_value":false,"is_enabled":true}]}""")

        val filters = (repository.leaderboardFilters() as NetworkResult.Success).data

        assertEquals(listOf("typeOverall"), filters.types.map { it.key })
        assertEquals(listOf("all_time"), filters.periods.map { it.key })
    }

    @Test
    fun `leaderboard sends type and period, empty QA board`() = runTest {
        enqueue("""{"success":true,"statusCode":200,"message":"Scores retrieved successfully","data":[]}""")

        val rows = (repository.leaderboard("typeQuiz", "weekly") as NetworkResult.Success).data

        assertEquals(
            "/api/v1/gamification/leaderboard/scores?leaderboardType=typeQuiz&periodType=weekly&limit=100",
            server.takeRequest().path,
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `leaderboard rows without a rank are ranked by position, snake_case fields`() = runTest {
        enqueue(
            """{"success":true,"data":[
                 {"learner_id":"u-1","full_name":"Asha","points":90,"xp":40},
                 {"learner_id":"u-2","learner_name":"Ravi","points":"40"},
                 {"learner_id":"u-3","points":5}
               ]}""",
        )

        val rows = (repository.leaderboard("typeOverall", "all_time") as NetworkResult.Success).data

        assertEquals(listOf("Asha", "Ravi", "Learner"), rows.map { it.name })
        assertEquals(listOf(1, 2, 3), rows.map { it.rank })
        assertEquals(listOf(90L, 40L, 5L), rows.map { it.points })
        assertEquals(listOf("u-1", "u-2", "u-3"), rows.map { it.learnerId })
        assertEquals(40L, rows[0].xp)
    }

    @Test
    fun `leaderboard rows with a server rank are sorted by it, nested learner name`() = runTest {
        enqueue(
            """{"success":true,"data":[
                 {"rank":2,"userId":"u-2","user":{"name":"Ravi"},"totalPoints":40},
                 {"rank":1,"learnerId":"u-1","learner":{"fullName":"Asha"},"totalPoints":90}
               ]}""",
        )

        val rows = (repository.leaderboard("typeOverall", "all_time") as NetworkResult.Success).data

        assertEquals(listOf(1 to "Asha", 2 to "Ravi"), rows.map { it.rank to it.name })
        assertEquals(listOf("u-1", "u-2"), rows.map { it.learnerId })
    }

    @Test
    fun `server error becomes a section error`() = runTest {
        enqueue("""{"success":false,"message":"column \"dob\" does not exist","stack":"..."}""", 500)

        val error = repository.progression() as NetworkResult.Error

        assertEquals(ErrorKind.Server, error.kind)
        assertFalse("the stack trace is never shown", error.message.contains("..."))
    }
}
