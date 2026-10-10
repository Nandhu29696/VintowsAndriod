package com.vintows.app.feature.assessments.data

import com.vintows.app.core.network.ApiCaller
import com.vintows.app.core.network.ErrorKind
import com.vintows.app.core.network.NetworkResult
import com.vintows.app.core.network.map
import com.vintows.app.core.session.SessionProvider
import com.vintows.app.feature.assessments.domain.AnswerValue
import com.vintows.app.feature.assessments.domain.Assessment
import com.vintows.app.feature.assessments.domain.AssessmentsRepository
import com.vintows.app.feature.assessments.domain.Attempt
import com.vintows.app.feature.assessments.domain.AttemptCheck
import com.vintows.app.feature.assessments.domain.Question
import com.vintows.app.feature.assessments.domain.TestResult
import com.vintows.app.feature.courses.data.CoursesApi
import com.vintows.app.feature.courses.domain.CoursesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssessmentsRepositoryImpl @Inject constructor(
    private val api: AssessmentsApi,
    private val coursesApi: CoursesApi,
    private val courses: CoursesRepository,
    private val apiCaller: ApiCaller,
    private val sessions: SessionProvider,
) : AssessmentsRepository {

    /**
     * There is no "all my tests" endpoint, so the list is built per level:
     * 1. every node of the level (`nodes?tableName=…` without a parent returns them all),
     * 2. one `counts` call per level for those ids,
     * 3. the tests of the nodes whose count is above zero.
     * About two calls per level plus one per node that has tests.
     */
    override suspend fun myTests(): NetworkResult<List<Assessment>> {
        val hierarchy = when (val h = courses.hierarchy()) {
            is NetworkResult.Success -> h.data
            is NetworkResult.Error -> return h
        }
        return try {
            coroutineScope {
                val perLevel = hierarchy.levels.map { level ->
                    async {
                        val nodes = when (val r = apiCaller.call { coursesApi.nodes(tableName = level.tableName) }) {
                            is NetworkResult.Success -> r.data
                            is NetworkResult.Error -> throw LoadFailure(r)
                        }
                        val names = nodes.mapNotNull { row ->
                            val id = (row["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                            id to ((row["name"] as? JsonPrimitive)?.contentOrNull ?: level.label)
                        }.toMap()
                        val withTests = names.keys.chunked(COUNT_BATCH).flatMap { ids ->
                            when (val r = apiCaller.call { api.counts(level.code, ids.joinToString(",")) }) {
                                is NetworkResult.Success -> r.data.filterValues { it > 0 }.keys
                                is NetworkResult.Error -> throw LoadFailure(r)
                            }
                        }
                        withTests.map { id ->
                            async {
                                when (val r = testsForNode(level.code, id, names[id])) {
                                    is NetworkResult.Success -> r.data
                                    is NetworkResult.Error -> throw LoadFailure(r)
                                }
                            }
                        }.awaitAll().flatten()
                    }
                }
                NetworkResult.Success(perLevel.awaitAll().flatten().distinctBy { it.id })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LoadFailure) {
            e.error
        }
    }

    override suspend fun testsForNode(levelCode: String, recordId: String, nodeName: String?): NetworkResult<List<Assessment>> {
        val call = if (includeDrafts()) {
            apiCaller.call { api.forStaff(levelCode, recordId) }
        } else {
            apiCaller.call { api.forStudent(levelCode, recordId) }
        }
        return call.map { rows -> rows.mapNotNull { it.toAssessment(nodeName) } }
    }

    override suspend fun check(assessmentId: String): NetworkResult<AttemptCheck> =
        apiCaller.call { api.check(assessmentId) }.map { AttemptCheck(it.canStart, it.maxAttempts, it.attemptsRemaining) }

    override suspend fun start(assessmentId: String): NetworkResult<Attempt> =
        attemptResult(apiCaller.call { api.start(StartAttemptRequest(assessmentId)) })

    override suspend fun attempt(attemptId: String): NetworkResult<Attempt> =
        attemptResult(apiCaller.call { api.attempt(attemptId) })

    override suspend fun submit(attemptId: String, questions: List<Question>, answers: Map<String, AnswerValue>): NetworkResult<TestResult> =
        apiCaller.call { api.submit(attemptId, SubmitAttemptRequest(answersPayload(questions, answers))) }.map { it.toTestResult() }

    override suspend fun reportProctoringEvent(attemptId: String, type: String, detail: String) {
        apiCaller.callUnit { api.proctoringEvent(attemptId, ProctoringEventRequest(type, detail)) }
    }

    private fun attemptResult(result: NetworkResult<JsonObject>): NetworkResult<Attempt> = when (result) {
        is NetworkResult.Error -> result
        is NetworkResult.Success -> result.data.toAttempt()?.let { NetworkResult.Success(it) }
            ?: NetworkResult.Error(ErrorKind.Unknown, "The test couldn't be opened. Please try again.")
    }

    /** Platform admins (debug preview) list drafts too; learners only get published tests. */
    private fun includeDrafts(): Boolean = sessions.current()?.isAdmin == true

    private class LoadFailure(val error: NetworkResult.Error) : Exception()

    private companion object {
        /** Keeps the `recordIds` query string well under URL length limits. */
        const val COUNT_BATCH = 40
    }
}
