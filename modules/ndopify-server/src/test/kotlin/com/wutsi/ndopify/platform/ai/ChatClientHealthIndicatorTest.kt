package com.wutsi.ndopify.platform.ai

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.boot.health.contributor.Status
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class ChatClientHealthIndicatorTest {
    private val chatModel = mock<ChatModel>()
    private val clock = mock<Clock>()
    private val now = Instant.parse("2026-10-28T10:00:00Z")
    private val health = ChatClientHealthIndicator(
        ChatClient.builder(chatModel).build(),
        chatModel,
        clock,
        Duration.ofMinutes(5),
        Duration.ofMinutes(1),
    )

    @BeforeEach
    fun setUp() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
        doReturn(now).whenever(clock).instant()
    }

    @Test
    fun up() {
        up(true)

        assertEquals(Status.UP, health.health().status)
        verify(chatModel).call(any<Prompt>())
    }

    @Test
    fun down() {
        up(false)

        val result = health.health()

        assertEquals(Status.DOWN, result.status)
        assertEquals(mapOf<String, Any>("error" to "RuntimeException"), result.details)
    }

    @Test
    fun `up is cached`() {
        up(true)
        health.health()

        doReturn(now.plus(Duration.ofMinutes(5)).minusMillis(1)).whenever(clock).instant()

        assertEquals(Status.UP, health.health().status)
        verify(chatModel, times(1)).call(any<Prompt>())
    }

    @Test
    fun `up is checked again once expired`() {
        up(true)
        health.health()

        up(false)
        doReturn(now.plus(Duration.ofMinutes(5))).whenever(clock).instant()

        assertEquals(Status.DOWN, health.health().status)
        verify(chatModel, times(2)).call(any<Prompt>())
    }

    @Test
    fun `down is cached`() {
        up(false)
        health.health()

        doReturn(now.plusSeconds(59)).whenever(clock).instant()

        assertEquals(Status.DOWN, health.health().status)
        verify(chatModel, times(1)).call(any<Prompt>())
    }

    @Test
    fun `down is checked again sooner`() {
        up(false)
        health.health()

        up(true)
        doReturn(now.plus(Duration.ofMinutes(1))).whenever(clock).instant()

        assertEquals(Status.UP, health.health().status)
        verify(chatModel, times(2)).call(any<Prompt>())
    }

    private fun up(up: Boolean) {
        if (up) {
            doReturn(ChatResponse(listOf(Generation(AssistantMessage("Hello")))))
                .whenever(chatModel).call(any<Prompt>())
        } else {
            doThrow(RuntimeException("provider error: model kimi-k2.6, key sk-xxx"))
                .whenever(chatModel).call(any<Prompt>())
        }
    }
}
