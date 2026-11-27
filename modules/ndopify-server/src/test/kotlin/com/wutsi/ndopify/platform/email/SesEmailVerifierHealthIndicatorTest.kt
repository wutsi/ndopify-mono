package com.wutsi.ndopify.platform.email

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.GetAccountRequest
import software.amazon.awssdk.services.sesv2.model.GetAccountResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SesEmailVerifierHealthIndicatorTest {
    private val client = mock<SesV2Client>()
    private val clock = MutableClock(Instant.parse("2026-11-27T10:00:00Z"))
    private val indicator = SesEmailVerifierHealthIndicator(
        client,
        clock,
        upTtl = Duration.ofMinutes(1),
        downTtl = Duration.ofSeconds(15),
    )

    @Test
    fun `UP carries the account flags and the latency`() {
        doReturn(account(sendingEnabled = true, productionAccessEnabled = false))
            .whenever(client).getAccount(any<GetAccountRequest>())

        val health = indicator.health()

        assertEquals("UP", health.status.code)
        assertEquals(true, health.details["sendingEnabled"])
        assertEquals(false, health.details["productionAccessEnabled"])
        assertTrue(health.details["latency"] is Number)
    }

    @Test
    fun `DOWN carries the exception class only`() {
        doThrow(RuntimeException("AKIA-secret ray@gmail.com"))
            .whenever(client).getAccount(any<GetAccountRequest>())

        val health = indicator.health()

        assertEquals("DOWN", health.status.code)
        assertEquals("RuntimeException", health.details["error"])
        assertEquals(setOf("error"), health.details.keys)
    }

    @Test
    fun `the UP answer is cached`() {
        doReturn(account()).whenever(client).getAccount(any<GetAccountRequest>())

        indicator.health()
        indicator.health()

        verify(client, times(1)).getAccount(any<GetAccountRequest>())
    }

    @Test
    fun `a recovery shows after the DOWN TTL`() {
        doThrow(RuntimeException("boom")).whenever(client).getAccount(any<GetAccountRequest>())
        assertEquals("DOWN", indicator.health().status.code)

        clock.instant = clock.instant.plus(Duration.ofSeconds(16))
        doReturn(account()).whenever(client).getAccount(any<GetAccountRequest>())

        assertEquals("UP", indicator.health().status.code)
        verify(client, times(2)).getAccount(any<GetAccountRequest>())
    }

    private fun account(sendingEnabled: Boolean = true, productionAccessEnabled: Boolean = true) =
        GetAccountResponse.builder()
            .sendingEnabled(sendingEnabled)
            .productionAccessEnabled(productionAccessEnabled)
            .build()

    private class MutableClock(
        var instant: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant
    }
}
