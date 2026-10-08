package com.vintows.app.core.session

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JwtDecoderTest {

    private val decoder = JwtDecoder(Json { ignoreUnknownKeys = true })

    private fun token(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        return "$header.${enc.encodeToString(payload.toByteArray())}.signature"
    }

    @Test
    fun `decodes claims from a Vintows access token`() {
        // Same claims as the real QA admin token.
        val jwt = token(
            """{"userId":3,"role":"SuperAdmin","email":"admin@example.com","type":"access","scope":"admin",
               "tenantId":null,"iat":1791381107,"exp":1791985907,"aud":"veskill-client","iss":"veskill-api"}""",
        )

        val claims = decoder.decode(jwt)!!

        assertEquals(3, claims.userId)
        assertEquals("SuperAdmin", claims.role)
        assertEquals("admin@example.com", claims.email)
        assertEquals("admin", claims.scope)
        assertNull(claims.tenantId)
        assertEquals(1791985907L, claims.expiresAt)
    }

    @Test
    fun `numeric tenantId is read as a string`() {
        val claims = decoder.decode(token("""{"userId":9,"tenantId":12,"scope":"licensed"}"""))!!

        assertEquals("12", claims.tenantId)
    }

    @Test
    fun `garbage input returns null`() {
        assertNull(decoder.decode("not-a-jwt"))
        assertNull(decoder.decode("a.%%%.c"))
    }

    @Test
    fun `session expiry uses exp`() {
        val session = Session(accessToken = "t", userId = 1, email = "e", expiresAtEpochSeconds = 1_000)

        assertFalse(session.isExpired(999))
        assertTrue(session.isExpired(1_000))
        assertFalse(session.copy(expiresAtEpochSeconds = null).isExpired(Long.MAX_VALUE))
    }
}
