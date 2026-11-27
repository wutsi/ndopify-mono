package com.wutsi.ndopify.email.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.email.server.dao.EmailAddressRepository
import com.wutsi.ndopify.email.server.domain.EmailAddressEntity
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.platform.email.EmailVerification
import com.wutsi.ndopify.platform.email.EmailVerificationThrottle
import com.wutsi.ndopify.platform.email.EmailVerifier
import com.wutsi.ndopify.platform.email.VerificationReason
import org.junit.jupiter.api.BeforeEach
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EmailVerificationServiceTest {
    companion object {
        private const val NOW = 1_700_000_000_000L
        private const val DAY = 24L * 60 * 60 * 1000
        private const val HASH = "16d8436dd78a031be9792507a5ecd0342f4e88df961e20d245596366b1b45313"
    }

    private val verifier = mock<EmailVerifier>()
    private val dao = mock<EmailAddressRepository>()
    private val throttle = mock<EmailVerificationThrottle>()
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)

    private lateinit var service: EmailVerificationService

    @BeforeEach
    fun setUp() {
        doReturn(true).whenever(throttle).tryAcquire(anyOrNull())
        service = EmailVerificationService(verifier, dao, throttle, clock, "ses", 30, 7)
    }

    @Test
    fun `a definitive valid verdict is cached for 30 days and accepted`() {
        doReturn(EmailVerification(1.0, VerificationReason.NONE)).whenever(verifier).verify("ray@gmail.com")

        service.ensureDeliverable("  Ray@Gmail.com ", "1.2.3.4")

        verify(verifier).verify("ray@gmail.com")
        val hash = argumentCaptor<String>()
        val expiresAt = argumentCaptor<Date>()
        verify(dao).upsert(
            hash.capture(), eq(1.0), eq("NONE"),
            eq("HIGH"), eq("HIGH"), eq("HIGH"), eq("HIGH"), eq("HIGH"), eq("HIGH"),
            any(), expiresAt.capture(),
        )
        assertEquals(HASH, hash.firstValue)
        assertEquals(Date(NOW + 30L * DAY), expiresAt.firstValue)
    }

    @Test
    fun `a LOW verdict is refused and cached for 7 days`() {
        doReturn(EmailVerification(0.0, VerificationReason.MAILBOX_UNREACHABLE)).whenever(verifier).verify(any())

        val ex = assertFailsWith<BadRequestException> { service.ensureDeliverable("ray@gmail.com", null) }

        assertEquals(ErrorCode.EMAIL_ADDRESS_INVALID, ex.error.code)
        assertEquals("email", ex.error.parameter!!.name)
        assertEquals(ParameterType.PARAMETER_TYPE_PAYLOAD, ex.error.parameter!!.type)
        assertNull(ex.error.message)
        assertNull(ex.error.data)
        val expiresAt = argumentCaptor<Date>()
        verify(dao).upsert(
            any(), eq(0.0), eq("MAILBOX_UNREACHABLE"),
            any(), any(), any(), any(), any(), any(),
            any(), expiresAt.capture(),
        )
        assertEquals(Date(NOW + 7L * DAY), expiresAt.firstValue)
    }

    @Test
    fun `a cached valid row inside its TTL is accepted with no call`() {
        doReturn(cached(1.0, Date(NOW + DAY))).whenever(dao).findByEmailHash(any())

        service.ensureDeliverable("ray@gmail.com", null)

        verify(verifier, never()).verify(any())
        verify(dao, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `a cached refused row inside its TTL is refused with no call`() {
        doReturn(cached(0.0, Date(NOW + DAY))).whenever(dao).findByEmailHash(any())

        assertFailsWith<BadRequestException> { service.ensureDeliverable("ray@gmail.com", null) }

        verify(verifier, never()).verify(any())
        verify(dao, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `an expired row is verified again and upserted`() {
        doReturn(cached(1.0, Date(NOW - 1))).whenever(dao).findByEmailHash(any())
        doReturn(EmailVerification(1.0, VerificationReason.NONE)).whenever(verifier).verify(any())

        service.ensureDeliverable("ray@gmail.com", null)

        verify(verifier).verify("ray@gmail.com")
        verify(dao).upsert(
            any(), eq(1.0), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        )
    }

    @Test
    fun `the provider none accepts without touching the cache, the budget or the verifier`() {
        service = EmailVerificationService(verifier, dao, throttle, clock, "none", 30, 7)

        service.ensureDeliverable("ray@gmail.com", "1.2.3.4")

        verify(dao, never()).findByEmailHash(any())
        verify(verifier, never()).verify(any())
        verify(throttle, never()).tryAcquire(anyOrNull())
    }

    @Test
    fun `a malformed address is refused locally, without a call nor a row`() {
        assertFailsWith<BadRequestException> { service.ensureDeliverable("ray@", null) }

        verify(verifier, never()).verify(any())
        verify(dao, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `a failing verifier fails open and caches nothing`() {
        doThrow(RuntimeException("boom ray@gmail.com")).whenever(verifier).verify(any())

        service.ensureDeliverable("ray@gmail.com", null)

        verify(dao, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `an unavailable verdict fails open and caches nothing`() {
        doReturn(EmailVerification(null, VerificationReason.UNAVAILABLE)).whenever(verifier).verify(any())

        service.ensureDeliverable("ray@gmail.com", null)

        verify(dao, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `a MEDIUM verdict is accepted and cached for 30 days`() {
        doReturn(EmailVerification(0.5, VerificationReason.NONE)).whenever(verifier).verify(any())

        service.ensureDeliverable("ray@gmail.com", null)

        val expiresAt = argumentCaptor<Date>()
        verify(dao).upsert(
            any(), eq(0.5), any(), any(), any(), any(), any(), any(), any(), any(), expiresAt.capture(),
        )
        assertEquals(Date(NOW + 30L * DAY), expiresAt.firstValue)
    }

    @Test
    fun `past the budget the submission is accepted without a call nor a row`() {
        doReturn(false).whenever(throttle).tryAcquire(anyOrNull())

        service.ensureDeliverable("ray@gmail.com", "1.2.3.4")

        verify(verifier, never()).verify(any())
        verify(dao, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    private fun cached(score: Double, expiresAt: Date) =
        EmailAddressEntity(emailHash = HASH, score = score, expiresAt = expiresAt)
}
