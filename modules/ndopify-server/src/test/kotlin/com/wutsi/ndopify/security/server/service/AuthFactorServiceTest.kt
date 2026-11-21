package com.wutsi.ndopify.security.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.server.dao.AuthFactorRepository
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthFactorServiceTest {
    private val dao = mock<AuthFactorRepository>()
    private val now = Instant.parse("2026-11-15T10:00:00Z")
    private val service = AuthFactorService(dao, Clock.fixed(now, ZoneOffset.UTC))

    private val factor = AuthFactorEntity(id = 1, authType = AuthType.OTP, data = "hash", salt = "salt")

    @BeforeTest
    fun setUp() {
        whenever(dao.save(any())).thenAnswer { it.arguments[0] as AuthFactorEntity }
    }

    @Test
    fun `first failure counts one`() {
        val saved = service.recordFailedAttempt(factor, 5, Duration.ofHours(1))

        assertEquals(1, saved.failedAttempts)
        assertNull(saved.lockedUntil)
    }

    @Test
    fun `fifth failure locks for the duration`() {
        val saved = service.recordFailedAttempt(factor.copy(failedAttempts = 4), 5, Duration.ofHours(1))

        assertEquals(5, saved.failedAttempts)
        assertEquals(now.toEpochMilli(), saved.lockedUntil!!.time - 3_600_000)
        assertTrue(saved.isLocked(Clock.fixed(now, ZoneOffset.UTC)))
    }

    @Test
    fun `an expired lock starts the count over`() {
        val expired = factor.copy(failedAttempts = 5, lockedUntil = Date(now.toEpochMilli() - 1000))

        val saved = service.recordFailedAttempt(expired, 5, Duration.ofHours(1))

        assertEquals(1, saved.failedAttempts)
        assertNull(saved.lockedUntil)
    }

    @Test
    fun `consume clears the count and the lock`() {
        val saved = service.consume(
            factor.copy(failedAttempts = 3, lockedUntil = Date(now.toEpochMilli() + 60_000)),
        )

        assertEquals(0, saved.failedAttempts)
        assertNull(saved.lockedUntil)
    }
}
