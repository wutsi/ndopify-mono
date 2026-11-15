package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.telegram.model.TelegramUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class TelegramHealthIndicatorTest {
    private val client = mock<TelegramClient>()
    private val clock = mock<Clock>()
    private val now = Instant.parse("2026-11-15T10:00:00Z")
    private val health = TelegramHealthIndicator(
        client,
        clock,
        Duration.ofMinutes(5),
        Duration.ofMinutes(1),
    )

    @BeforeEach
    fun setUp() {
        doReturn(now).whenever(clock).instant()
    }

    @Test
    fun up() {
        doReturn(TelegramUser(id = 1L, username = "ndopify_bot")).whenever(client).getMe()

        val result = health.health()

        assertEquals(Status.UP, result.status)
        assertEquals("ndopify_bot", result.details["username"])
    }

    @Test
    fun down() {
        doThrow(RuntimeException("token 123:secret leaked")).whenever(client).getMe()

        val result = health.health()

        assertEquals(Status.DOWN, result.status)
        assertEquals(mapOf<String, Any>("error" to "RuntimeException"), result.details)
    }

    @Test
    fun `up is cached`() {
        doReturn(TelegramUser(id = 1L, username = "ndopify_bot")).whenever(client).getMe()
        health.health()

        doReturn(now.plus(Duration.ofMinutes(5)).minusMillis(1)).whenever(clock).instant()

        assertEquals(Status.UP, health.health().status)
        verify(client, times(1)).getMe()
    }

    @Test
    fun `up is checked again once expired`() {
        doReturn(TelegramUser(id = 1L, username = "ndopify_bot")).whenever(client).getMe()
        health.health()

        doThrow(RuntimeException("down")).whenever(client).getMe()
        doReturn(now.plus(Duration.ofMinutes(5))).whenever(clock).instant()

        assertEquals(Status.DOWN, health.health().status)
        verify(client, times(2)).getMe()
    }

    @Test
    fun `down is cached`() {
        doThrow(RuntimeException("down")).whenever(client).getMe()
        health.health()

        doReturn(now.plusSeconds(59)).whenever(clock).instant()

        assertEquals(Status.DOWN, health.health().status)
        verify(client, times(1)).getMe()
    }

    @Test
    fun `down is checked again sooner`() {
        doThrow(RuntimeException("down")).whenever(client).getMe()
        health.health()

        doReturn(TelegramUser(id = 1L, username = "ndopify_bot")).whenever(client).getMe()
        doReturn(now.plus(Duration.ofMinutes(1))).whenever(clock).instant()

        assertEquals(Status.UP, health.health().status)
        verify(client, times(2)).getMe()
    }
}
