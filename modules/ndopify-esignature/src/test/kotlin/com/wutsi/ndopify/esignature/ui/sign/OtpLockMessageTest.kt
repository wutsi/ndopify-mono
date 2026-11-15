package com.wutsi.ndopify.esignature.ui.sign

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class OtpLockMessageTest {
    private val now = Instant.parse("2026-11-15T10:00:00Z").toEpochMilli()

    @Test
    fun `locked - plural minutes`() {
        assertEquals(
            "Trop de tentatives. Réessayez dans 42 minutes.",
            OtpLockMessage.locked(mapOf("lockedUntil" to "2026-11-15T10:42:00Z"), now),
        )
    }

    @Test
    fun `locked - one minute`() {
        assertEquals(
            "Trop de tentatives. Réessayez dans 1 minute.",
            OtpLockMessage.locked(mapOf("lockedUntil" to "2026-11-15T10:00:30Z"), now),
        )
    }

    @Test
    fun `locked - no data`() {
        assertEquals("Trop de tentatives. Réessayez plus tard.", OtpLockMessage.locked(null, now))
    }

    @Test
    fun `locked - unreadable lock`() {
        assertEquals("Trop de tentatives. Réessayez plus tard.", OtpLockMessage.locked(mapOf("lockedUntil" to "soon"), now))
    }

    @Test
    fun `invalid - remaining attempts`() {
        assertEquals(
            "Code incorrect. Encore 2 tentatives avant le blocage de votre compte.",
            OtpLockMessage.invalid(mapOf("remainingAttempts" to 2), "Code incorrect."),
        )
        assertEquals(
            "Code incorrect. Encore 1 tentative avant le blocage de votre compte.",
            OtpLockMessage.invalid(mapOf("remainingAttempts" to 1), "Code incorrect."),
        )
        assertEquals("Code incorrect.", OtpLockMessage.invalid(null, "Code incorrect."))
    }
}
