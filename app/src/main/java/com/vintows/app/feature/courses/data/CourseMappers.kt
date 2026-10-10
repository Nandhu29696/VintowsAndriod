package com.vintows.app.feature.courses.data

import com.vintows.app.feature.courses.domain.ContentItem
import com.vintows.app.feature.courses.domain.ContentType
import com.vintows.app.feature.courses.domain.CourseHierarchy
import com.vintows.app.feature.courses.domain.CourseLevel
import com.vintows.app.feature.courses.domain.CourseNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class LevelConfigDto(val levels: List<CourseLevelDto> = emptyList())

@Serializable
data class CourseLevelDto(
    val code: String = "",
    val tableName: String = "",
    val label: String = "",
    val order: Int = 0,
    val enabled: Boolean = false,
)

internal fun LevelConfigDto.toHierarchy() = CourseHierarchy(
    levels
        .filter { it.enabled && it.code.isNotBlank() && it.tableName.isNotBlank() }
        .sortedBy { it.order }
        .map { CourseLevel(code = it.code, label = it.label.ifBlank { it.code.lowercase().replaceFirstChar(Char::uppercase) }, tableName = it.tableName, order = it.order) },
)

// Rows are read as raw JSON: ids are UUIDs here but numeric in other tables, and price arrives as "0.00".

private fun JsonObject.str(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
}

private fun JsonObject.bool(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
    (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }
}

private fun JsonObject.num(vararg keys: String): Double? = keys.firstNotNullOfOrNull { key ->
    (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
}

/** Inactive or deleted rows are dropped by the caller. */
internal fun JsonObject.isLive(): Boolean = bool("is_active", "isActive") != false && str("deleted_at", "deletedAt") == null

internal fun JsonObject.sequence(): Double = num("sequence_number", "sequenceNumber") ?: Double.MAX_VALUE

internal fun JsonObject.toCourseNode(levelCode: String): CourseNode? {
    val id = str("id") ?: return null
    return CourseNode(
        id = id,
        name = str("name", "title") ?: "Untitled",
        levelCode = levelCode,
        description = str("description"),
        isPaid = bool("is_paid", "isPaid") ?: false,
        price = str("price"),
        duration = num("duration_value", "durationValue")?.takeIf { it > 0 }?.let { value ->
            val unit = str("duration_unit", "durationUnit", "duration_unit_name") ?: "min"
            "${value.toInt()} $unit"
        },
        contentUrl = str("content_url", "contentUrl"),
    )
}

internal fun JsonObject.toContentItem(type: ContentType): ContentItem? {
    val id = str("id") ?: return null
    return ContentItem(
        type = type,
        id = id,
        name = str("name", "title", "file_name", "fileName") ?: type.label,
        status = str("content_status", "contentStatus", "status"),
        html = str("html", "content_html"),
        code = if (type == ContentType.CodeFile) (this["content"] as? JsonPrimitive)?.contentOrNull else null,
        language = str("language"),
        url = str("url", "share_url", "shareUrl", "embed_url", "embedUrl", "file_url", "fileUrl"),
        mimeType = str("mime_type", "mimeType"),
        fileSize = num("file_size", "fileSize")?.toLong(),
        allowDownload = bool("allow_download", "allowDownload") ?: true,
        elementCount = (this["elements"] as? JsonArray)?.size ?: 0,
    )
}
