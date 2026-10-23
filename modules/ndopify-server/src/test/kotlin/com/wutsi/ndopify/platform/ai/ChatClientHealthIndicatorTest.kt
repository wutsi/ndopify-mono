package com.wutsi.ndopify.platform.ai

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
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
import kotlin.test.assertEquals

class ChatClientHealthIndicatorTest {
    private val chatModel = mock<ChatModel>()
    private val health = ChatClientHealthIndicator(ChatClient.builder(chatModel).build(), chatModel)

    @BeforeEach
    fun setUp() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
    }

    @Test
    fun up() {
        doReturn(ChatResponse(listOf(Generation(AssistantMessage("Hello")))))
            .whenever(chatModel).call(any<Prompt>())

        assertEquals(Status.UP, health.health().status)
        verify(chatModel).call(any<Prompt>())
    }

    @Test
    fun down() {
        doThrow(RuntimeException::class).whenever(chatModel).call(any<Prompt>())

        assertEquals(Status.DOWN, health.health().status)
    }
}
