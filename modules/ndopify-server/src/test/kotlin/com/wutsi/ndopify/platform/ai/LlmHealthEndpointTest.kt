package com.wutsi.ndopify.platform.ai

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmHealthEndpointTest : BaseEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var chatModel: ChatModel

    @BeforeEach
    fun stubChatModel() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
        doReturn(ChatResponse(listOf(Generation(AssistantMessage("Hello")))))
            .whenever(chatModel).call(any<Prompt>())
    }

    @Test
    fun notInDefaultGroup() {
        val response = rest.getForEntity("/actuator/health", Map::class.java)

        val components = components(response.body!!)
        assertFalse(components.containsKey(ChatClientHealthEndpointGroupsPostProcessor.CONTRIBUTOR))
        assertTrue(components.containsKey("db"))
        verify(chatModel, never()).call(any<Prompt>())
    }

    @Test
    fun inLlmGroup() {
        val response = rest.getForEntity("/actuator/health/llm", Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(setOf(ChatClientHealthEndpointGroupsPostProcessor.CONTRIBUTOR), components(response.body!!).keys)
        verify(chatModel).call(any<Prompt>())
    }

    @Suppress("UNCHECKED_CAST")
    private fun components(body: Map<*, *>): Map<String, Any> = body["components"] as Map<String, Any>
}
