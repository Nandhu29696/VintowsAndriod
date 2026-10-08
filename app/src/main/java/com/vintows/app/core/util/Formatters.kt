package com.vintows.app.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Two-letter initials for avatars. Works for names and emails:
 * "John Smith" → "JS", "dev.team@example.com" → "DT", "admin" → "AD".
 */
fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("[\\s@._-]+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2)
        else -> "${parts[0].first()}${parts[1].first()}"
    }.uppercase(Locale.ROOT)
}

/** Formats the API's ISO-8601 UTC timestamps (e.g. "2026-08-01T08:04:01.385Z") for display. */
object DateFormatter {
    private val dateTime = DateTimeFormatter.ofPattern("MMM d, hh:mm a", Locale.ENGLISH)
    private val dateOnly = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    /** "Aug 1, 01:34 PM" in the given zone (device zone by default). Returns "" for bad input. */
    fun dateTime(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String =
        format(iso, dateTime, zone)

    /** "Aug 1" */
    fun date(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String =
        format(iso, dateOnly, zone)

    private fun format(iso: String?, formatter: DateTimeFormatter, zone: ZoneId): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            formatter.format(Instant.parse(iso).atZone(zone))
        } catch (_: DateTimeParseException) {
            ""
        }
    }
}
