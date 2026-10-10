package com.vintows.app.feature.assessments.data

import com.vintows.app.core.network.ApiEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Assessment and test-attempt endpoints, called exactly like the web test player
 * (`test-attempts.service` and `course-builder.service` in the web bundle).
 */
interface AssessmentsApi {
    /** `{ "<recordId>": <number of tests>, … }` for several nodes of one level. Counts include drafts. */
    @GET("course-builder/assessments/counts")
    suspend fun counts(
        @Query("levelCode") levelCode: String,
        @Query("recordIds") recordIds: String,
    ): ApiEnvelope<Map<String, Int>>

    /** Published tests of one node, as a learner sees them. */
    @GET("course-builder/assessments/for-student")
    suspend fun forStudent(
        @Query("levelCode") levelCode: String,
        @Query("recordId") recordId: String,
    ): ApiEnvelope<List<JsonObject>>

    /** Every test of one node including drafts; used only for the admin preview. */
    @GET("course-builder/assessments")
    suspend fun forStaff(
        @Query("levelCode") levelCode: String,
        @Query("recordId") recordId: String,
    ): ApiEnvelope<List<JsonObject>>

    /** `{ canStart, maxAttempts, attemptsRemaining }`; HTTP 400 "This test has no questions yet." for empty tests. */
    @GET("test-attempts/check")
    suspend fun check(@Query("assessmentId") assessmentId: String): ApiEnvelope<AttemptCheckDto>

    /** Starts an attempt: `{ id, questions, timeLimitMinutes, maxAttempts, attemptsRemaining, proctored, … }`. */
    @POST("test-attempts")
    suspend fun start(@Body body: StartAttemptRequest): ApiEnvelope<JsonObject>

    @GET("test-attempts/{id}")
    suspend fun attempt(@Path("id") attemptId: String): ApiEnvelope<JsonObject>

    /** Returns the result, at least `{ score, maxScore }`. */
    @POST("test-attempts/{id}/submit")
    suspend fun submit(@Path("id") attemptId: String, @Body body: SubmitAttemptRequest): ApiEnvelope<JsonObject>

    @POST("test-attempts/{id}/proctoring-event")
    suspend fun proctoringEvent(@Path("id") attemptId: String, @Body body: ProctoringEventRequest): ApiEnvelope<JsonElement>
}

@Serializable
data class AttemptCheckDto(
    val canStart: Boolean = true,
    val maxAttempts: Int? = null,
    val attemptsRemaining: Int? = null,
)

// Request bodies have no default values: kotlinx.serialization leaves out fields equal to their default.

@Serializable
data class StartAttemptRequest(val assessmentId: String)

/** `answer` is a list of 1-based option numbers for choice questions, or text. */
@Serializable
data class AnswerDto(val questionId: JsonElement, val answer: JsonElement)

@Serializable
data class SubmitAttemptRequest(val answers: List<AnswerDto>)

/** `type` is `tab_switch` when the learner leaves the app; `detail` is "count/limit" like the web. */
@Serializable
data class ProctoringEventRequest(val type: String, val detail: String)
