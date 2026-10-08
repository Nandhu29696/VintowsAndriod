package com.vintows.app.feature.notifications

import com.vintows.app.core.network.ApiEnvelope
import com.vintows.app.feature.notifications.data.NotificationPageDto
import com.vintows.app.feature.notifications.data.toAppNotification
import com.vintows.app.testing.testJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationMappingTest {

    private fun map(json: String, fallbackId: Int = 0) = testJson.parseToJsonElement(json).jsonObject.toAppNotification(fallbackId)

    @Test
    fun `parses the real (empty) QA page`() {
        val page = testJson.decodeFromString<ApiEnvelope<NotificationPageDto>>(
            """{"success":true,"message":"Notifications listed","data":{"items":[],"total":0,"page":1,"limit":20,
               "totalPages":0,"hasNextPage":false,"hasPreviousPage":false}}""",
        ).data!!

        assertTrue(page.items.isEmpty())
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `camelCase push-style item`() {
        val n = map("""{"id":"n1","title":"New lesson","body":"Arrays are live","createdAt":"2026-10-08T10:00:00Z","read":true}""")

        assertEquals("n1", n.id)
        assertEquals("New lesson", n.title)
        assertEquals("Arrays are live", n.body)
        assertEquals("2026-10-08T10:00:00Z", n.createdAt)
        assertTrue(n.read)
    }

    @Test
    fun `snake_case notifications-table item`() {
        val n = map("""{"id":"u-1","email_subject":"Test reminder","email_body":"Quiz at 5 PM","sent_date":"2026-10-08T11:00:00Z","status":"SENT"}""")

        assertEquals("Test reminder", n.title)
        assertEquals("Quiz at 5 PM", n.body)
        assertEquals("2026-10-08T11:00:00Z", n.createdAt)
        assertFalse(n.read)
    }

    @Test
    fun `missing fields fall back safely`() {
        val n = map("""{"message":"Hello"}""", fallbackId = 7)

        assertEquals("7", n.id)
        assertEquals("Notification", n.title)
        assertEquals("Hello", n.body)
        assertNull(n.createdAt)
    }
}
