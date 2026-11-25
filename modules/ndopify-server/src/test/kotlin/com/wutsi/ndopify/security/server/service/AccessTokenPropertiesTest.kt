package com.wutsi.ndopify.security.server.service

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccessTokenPropertiesTest {
    private val properties = AccessTokenProperties()
    private val now = Instant.parse("2026-11-24T00:00:00Z")

    @Test
    fun `a renewal gives the token's own lifetime, not what is left of it`() {
        assertEquals(
            Duration.ofDays(30),
            properties.renewal(
                lifetime = Duration.ofDays(30),
                now = now,
                expiresAt = now.plus(Duration.ofDays(10)),
                sessionStartedAt = now.minus(Duration.ofDays(20)),
            ),
        )
    }

    @Test
    fun `a renewal near the cap gives only what the cap leaves`() {
        assertEquals(
            Duration.ofDays(5),
            properties.renewal(
                lifetime = Duration.ofDays(30),
                now = now,
                expiresAt = now.plus(Duration.ofDays(10)),
                sessionStartedAt = now.minus(Duration.ofDays(85)),
            ),
        )
    }

    @Test
    fun `a session at or past the cap is not renewed`() {
        assertNull(
            properties.renewal(Duration.ofDays(30), now, now.plus(Duration.ofDays(10)), now.minus(Duration.ofDays(90))),
        )
        assertNull(
            properties.renewal(Duration.ofDays(30), now, now.plus(Duration.ofDays(10)), now.minus(Duration.ofDays(91))),
        )
    }

    @Test
    fun `a longer-lived token keeps its original expiry`() {
        val expiresAt = now.plus(Duration.ofDays(355))

        assertEquals(
            Duration.ofDays(355),
            properties.renewal(Duration.ofDays(365), now, expiresAt, now.minus(Duration.ofDays(10))),
        )
    }

    @Test
    fun `a longer-lived token past the cap is not renewed`() {
        assertNull(
            properties.renewal(Duration.ofDays(365), now, now.plus(Duration.ofDays(265)), now.minus(Duration.ofDays(100))),
        )
    }
}
