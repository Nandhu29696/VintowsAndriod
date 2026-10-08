package com.vintows.app.feature.notifications.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

data class AppNotification(
    val id: String,
    val title: String,
    val body: String,
    /** ISO-8601 timestamp, or null. */
    val createdAt: String?,
    val read: Boolean,
)

/**
 * Tolerant mapping: tries camelCase and snake_case variants of the likely field names
 * (the backend table `notifications` has email_subject / email_body / sent_date / status).
 */
internal fun JsonObject.toAppNotification(fallbackId: Int): AppNotification {
    fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
    fun bool(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.booleanOrNull }

    val readFlag = bool("read", "isRead", "is_read") ?: str("status")?.equals("read", ignoreCase = true) ?: false
    return AppNotification(
        id = str("id", "primaryid", "_id") ?: fallbackId.toString(),
        title = str("title", "subject", "emailSubject", "email_subject", "notificationType", "notification_type") ?: "Notification",
        body = str("body", "message", "content", "emailBody", "email_body", "remarks") ?: "",
        createdAt = str("createdAt", "created_at", "sentDate", "sent_date", "timestamp"),
        read = readFlag,
    )
}
