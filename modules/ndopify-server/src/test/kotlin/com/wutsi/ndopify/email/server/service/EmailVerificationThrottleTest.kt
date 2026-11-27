package com.wutsi.ndopify.email.server.service

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailVerificationThrottleTest {
    private val clock = MutableClock(Instant.parse("2026-11-27T10:00:00Z"))

    @Test
    fun `the per-IP window refuses the 21st call and another address still passes`() {
        val throttle = EmailVerificationThrottle(clock, 20, 1000)

        repeat(20) { assertTrue(throttle.tryAcquire("1.2.3.4")) }
        assertFalse(throttle.tryAcquire("1.2.3.4"))
        assertTrue(throttle.tryAcquire("5.6.7.8"))
    }

    @Test
    fun `the window forgets calls older than an hour`() {
        val throttle = EmailVerificationThrottle(clock, 20, 1000)
        repeat(20) { throttle.tryAcquire("1.2.3.4") }

        clock.instant = clock.instant.plus(Duration.ofMinutes(61))

        assertTrue(throttle.tryAcquire("1.2.3.4"))
    }

    @Test
    fun `the daily cap counts every call, whatever the IP`() {
        val throttle = EmailVerificationThrottle(clock, 20, 2)

        assertTrue(throttle.tryAcquire("1.2.3.4"))
        assertTrue(throttle.tryAcquire("5.6.7.8"))
        assertFalse(throttle.tryAcquire("9.10.11.12"))
    }

    @Test
    fun `a null IP counts against the daily cap only`() {
        val throttle = EmailVerificationThrottle(clock, 1, 3)

        assertTrue(throttle.tryAcquire(null))
        assertTrue(throttle.tryAcquire(null))
        assertTrue(throttle.tryAcquire(null))
        assertFalse(throttle.tryAcquire(null))
    }

    @Test
    fun `the daily counter resets on the next UTC day`() {
        val throttle = EmailVerificationThrottle(clock, 20, 1)
        assertTrue(throttle.tryAcquire("1.2.3.4"))
        assertFalse(throttle.tryAcquire("1.2.3.4"))

        clock.instant = Instant.parse("2026-11-28T00:00:01Z")

        assertTrue(throttle.tryAcquire("1.2.3.4"))
    }

    private class MutableClock(
        var instant: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant
    }
}
