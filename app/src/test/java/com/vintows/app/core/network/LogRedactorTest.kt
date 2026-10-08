package com.vintows.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogRedactorTest {

    @Test
    fun `masks the login password but keeps the email`() {
        val logged = LogRedactor.redact("""{"email":"a@b.com","mailId":"a@b.com","password":"Secret@123"}""")

        assertEquals("""{"email":"a@b.com","mailId":"a@b.com","password":"██"}""", logged)
    }

    @Test
    fun `masks tokens in the login response`() {
        val logged = LogRedactor.redact("""{"data":{"accessToken":"eyJhbGc.x.y","refreshToken": "eyJr.z.w","user":{"id":3}}}""")

        assertFalse(logged.contains("eyJ"))
        assertEquals("""{"data":{"accessToken":"██","refreshToken":"██","user":{"id":3}}}""", logged)
    }

    @Test
    fun `masks bearer headers`() {
        assertEquals("Authorization: Bearer ██", LogRedactor.redact("Authorization: Bearer eyJhbGciOi.abc-_.def"))
    }

    @Test
    fun `leaves ordinary lines alone`() {
        val line = "--> POST https://qa.vintows.com/api/v1/auth/login"
        assertEquals(line, LogRedactor.redact(line))
    }
}
