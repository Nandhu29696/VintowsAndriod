package com.vintows.app.feature.assessments.domain

import com.vintows.app.core.network.NetworkResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A test or assignment attached to a course node. */
data class Assessment(
    val id: String,
    val name: String,
    /** `TEST` or `ASSIGNMENT`. */
    val type: String,
    val levelCode: String,
    val recordId: String,
    /** Name of the subject/chapter/lesson it belongs to, when known. */
    val nodeName: String? = null,
    val timeLimitMinutes: Int? = null,
    val maxAttempts: Int? = null,
    val passMark: Double? = null,
    val totalMarks: Double? = null,
    val proctored: Boolean = false,
    val negativeMarking: Boolean = false,
    val status: String? = null,
) {
    val isDraft: Boolean get() = status.equals("DRAFT", ignoreCase = true)
    val isAssignment: Boolean get() = type.equals("ASSIGNMENT", ignoreCase = true)
}

data class AttemptCheck(val canStart: Boolean, val maxAttempts: Int?, val attemptsRemaining: Int?)

/** How a question is answered. API types are mapped like the web player's `mapType`. */
enum class QuestionKind {
    SingleChoice, MultipleChoice, TrueFalse, FillBlank, ShortText, LongText;

    val isChoice: Boolean get() = this == SingleChoice || this == MultipleChoice

    companion object {
        fun of(apiType: String?): QuestionKind = when (apiType?.uppercase()) {
            "MCQ", "MULTIPLE_QUESTIONS" -> MultipleChoice
            "SCQ", "IMAGE_QUESTION" -> SingleChoice
            "TRUE_FALSE" -> TrueFalse
            "FILL_BLANK" -> FillBlank
            "LONG_ANSWER" -> LongText
            else -> ShortText
        }
    }
}

data class Question(
    /** Stable key for local state. */
    val key: String,
    /** The id exactly as the server sent it (number or UUID), echoed back on submit. */
    val serverId: JsonElement,
    val kind: QuestionKind,
    val text: String,
    val options: List<String> = emptyList(),
    val marks: Double? = null,
    val imageUrl: String? = null,
) {
    /** Fill-in-the-blank text split on `___`; one input per gap. */
    val blankParts: List<String> get() = text.split("___")
    val blankCount: Int get() = if (kind == QuestionKind.FillBlank) (blankParts.size - 1).coerceAtLeast(1) else 0
}

data class Attempt(
    val id: String,
    val questions: List<Question>,
    val timeLimitMinutes: Int?,
    /** Server start time (epoch ms), when the response carries one. */
    val startedAtMillis: Long?,
    val maxAttempts: Int?,
    val attemptsRemaining: Int?,
    val proctored: Boolean,
)

/** A learner's answer to one question. Stored locally while the test is running. */
@Serializable
sealed interface AnswerValue {
    /** 1-based option numbers. True/false uses 1 = True, 2 = False (web convention). */
    @Serializable
    data class Choices(val options: List<Int>) : AnswerValue

    @Serializable
    data class Text(val text: String) : AnswerValue

    @Serializable
    data class Blanks(val values: List<String>) : AnswerValue

    val isAnswered: Boolean
        get() = when (this) {
            is Choices -> options.isNotEmpty()
            is Text -> text.isNotBlank()
            is Blanks -> values.any { it.isNotBlank() }
        }
}

data class TestResult(
    val score: Double?,
    val maxScore: Double?,
    val passed: Boolean?,
    /** True when some answers (short/long text) wait for a teacher's marks. */
    val needsReview: Boolean,
) {
    val fraction: Float?
        get() = if (score != null && maxScore != null && maxScore > 0) (score / maxScore).toFloat().coerceIn(0f, 1f) else null
}

/** Progress of a running attempt, kept on the device so a killed app can resume it. */
@Serializable
data class SavedAttempt(
    val attemptId: String,
    val startedAtMillis: Long,
    val answers: Map<String, AnswerValue> = emptyMap(),
    val flagged: Set<String> = emptySet(),
)

interface AssessmentsRepository {
    /** Every test the learner can see, across all course levels. */
    suspend fun myTests(): NetworkResult<List<Assessment>>

    suspend fun testsForNode(levelCode: String, recordId: String, nodeName: String? = null): NetworkResult<List<Assessment>>

    suspend fun check(assessmentId: String): NetworkResult<AttemptCheck>

    suspend fun start(assessmentId: String): NetworkResult<Attempt>

    suspend fun attempt(attemptId: String): NetworkResult<Attempt>

    suspend fun submit(attemptId: String, questions: List<Question>, answers: Map<String, AnswerValue>): NetworkResult<TestResult>

    /** Fire-and-forget proctoring log; failures are ignored like on the web. */
    suspend fun reportProctoringEvent(attemptId: String, type: String, detail: String)
}
