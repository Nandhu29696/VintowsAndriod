package com.vintows.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class FormattersTest {

    private val ist = ZoneId.of("Asia/Kolkata")

    @Test
    fun `formats api timestamp like the web app`() {
        // SR-0001 was created at 08:04 UTC, shown on the web as "Aug 1, 01:34 PM" (IST).
        assertEquals("Aug 1, 01:34 PM", DateFormatter.dateTime("2026-08-01T08:04:01.385Z", ist))
        assertEquals("Aug 1", DateFormatter.date("2026-08-01T08:04:01.385Z", ist))
    }

    @Test
    fun `bad or missing timestamps give empty text`() {
        assertEquals("", DateFormatter.dateTime(null, ist))
        assertEquals("", DateFormatter.dateTime("yesterday", ist))
    }

    @Test
    fun `initials from names and emails`() {
        assertEquals("DT", initialsOf("dev.team@example.com"))
        assertEquals("JS", initialsOf("John Smith"))
        assertEquals("AD", initialsOf("admin"))
        assertEquals("?", initialsOf("  "))
    }
}
