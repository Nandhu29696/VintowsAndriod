package com.vintows.app.feature.assessments.data

import com.vintows.app.feature.assessments.domain.AnswerValue
import com.vintows.app.feature.assessments.domain.Assessment
import com.vintows.app.feature.assessments.domain.Attempt
import com.vintows.app.feature.assessments.domain.Question
import com.vintows.app.feature.assessments.domain.QuestionKind
import com.vintows.app.feature.assessments.domain.TestResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant

// The test player's response shapes are taken from the web bundle (QA tests have no questions yet),
// so every read accepts camelCase and snake_case.

private fun JsonObject.prim(vararg keys: String): JsonPrimitive? =
    keys.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.takeIf { p -> p !is JsonNull } }

private fun JsonObject.str(vararg keys: String): String? = prim(*keys)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.num(vararg keys: String): Double? = prim(*keys)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

private fun JsonObject.bool(vararg keys: String): Boolean? = prim(*keys)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }

private fun JsonObject.obj(vararg keys: String): JsonObject? = keys.firstNotNullOfOrNull { this[it] as? JsonObject }

internal fun JsonObject.toAssessment(nodeName: String? = null): Assessment? {
    val id = str("id") ?: return null
    // Settings live in a nested `settings` object on QA; read top-level copies too.
    val settings = obj("settings") ?: JsonObject(emptyMap())
    fun sNum(vararg keys: String) = settings.num(*keys) ?: num(*keys)
    return Assessment(
        id = id,
        name = str("name", "title") ?: "Test",
        type = str("type") ?: "TEST",
        levelCode = str("level_code", "levelCode").orEmpty(),
        recordId = str("record_id", "recordId").orEmpty(),
        nodeName = nodeName ?: str("node_name", "nodeName"),
        timeLimitMinutes = sNum("timeLimitMinutes", "time_limit_minutes")?.toInt()?.takeIf { it > 0 },
        maxAttempts = sNum("maxAttempts", "max_attempts")?.toInt()?.takeIf { it > 0 },
        passMark = sNum("passMark", "pass_mark"),
        totalMarks = sNum("totalMarks", "total_marks"),
        proctored = settings.bool("proctored") ?: bool("proctored") ?: false,
        negativeMarking = settings.bool("negativeMarking") ?: false,
        status = str("content_status", "contentStatus", "status"),
    )
}

internal fun JsonObject.toQuestion(index: Int): Question {
    val serverId = this["id"]?.takeIf { it !is JsonNull } ?: JsonPrimitive(index + 1)
    val options = (this["options"] as? JsonArray).orEmpty().mapNotNull { option ->
        when (option) {
            is JsonPrimitive -> option.contentOrNull
            // Some editors store options as objects; take their text.
            is JsonObject -> option.str("text", "label", "value", "option")
            else -> null
        }
    }
    return Question(
        key = (serverId as? JsonPrimitive)?.contentOrNull ?: "q${index + 1}",
        serverId = serverId,
        kind = QuestionKind.of(str("type", "question_type", "questionType")),
        text = str("text", "question_text", "questionText", "question").orEmpty(),
        options = options,
        marks = num("marks", "mark", "points"),
        imageUrl = str("image_url", "imageUrl", "image"),
    )
}

internal fun JsonObject.toAttempt(): Attempt? {
    // Some responses wrap the attempt as { attempt: {…}, questions: […] }.
    val attempt = obj("attempt") ?: this
    val id = attempt.str("id", "attemptId", "attempt_id") ?: return null
    val questions = ((this["questions"] ?: attempt["questions"]) as? JsonArray).orEmpty()
        .mapIndexedNotNull { index, element -> (element as? JsonObject)?.toQuestion(index) }
    val settings = attempt.obj("settings") ?: JsonObject(emptyMap())
    return Attempt(
        id = id,
        questions = questions,
        timeLimitMinutes = (attempt.num("timeLimitMinutes", "time_limit_minutes") ?: settings.num("timeLimitMinutes"))?.toInt()?.takeIf { it > 0 },
        startedAtMillis = attempt.str("startedAt", "started_at")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        maxAttempts = attempt.num("maxAttempts", "max_attempts")?.toInt(),
        attemptsRemaining = attempt.num("attemptsRemaining", "attempts_remaining")?.toInt(),
        proctored = attempt.bool("proctored") ?: settings.bool("proctored") ?: false,
    )
}

internal fun JsonObject.toTestResult(): TestResult {
    val r = obj("result", "attempt") ?: this
    return TestResult(
        score = r.num("score", "totalScore", "total_score"),
        maxScore = r.num("maxScore", "max_score", "totalMarks", "total_marks"),
        passed = r.bool("passed", "isPassed", "is_passed"),
        needsReview = r.bool("needsReview", "needs_review") ?: false,
    )
}

/**
 * Builds the submit body the way the web player does: every question is sent, unanswered ones with an
 * empty list (choices) or empty text. Fill-in-the-blank values are joined with spaces.
 */
internal fun answersPayload(questions: List<Question>, answers: Map<String, AnswerValue>): List<AnswerDto> =
    questions.map { q ->
        val value = answers[q.key]
        val answer: JsonElement = when (q.kind) {
            QuestionKind.SingleChoice, QuestionKind.MultipleChoice, QuestionKind.TrueFalse ->
                JsonArray(((value as? AnswerValue.Choices)?.options ?: emptyList()).sorted().map { JsonPrimitive(it) })
            QuestionKind.FillBlank ->
                JsonPrimitive((value as? AnswerValue.Blanks)?.values?.filter { it.isNotBlank() }?.joinToString(" ") { it.trim() }.orEmpty())
            QuestionKind.ShortText, QuestionKind.LongText ->
                JsonPrimitive((value as? AnswerValue.Text)?.text?.trim().orEmpty())
        }
        AnswerDto(questionId = q.serverId, answer = answer)
    }
