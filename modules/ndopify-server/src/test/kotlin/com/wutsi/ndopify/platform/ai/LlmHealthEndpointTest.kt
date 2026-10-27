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
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.deepseek.DeepSeekChatModel
import org.springframework.ai.deepseek.DeepSeekChatOptions
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmHealthEndpointTest : BaseEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var visionModel: OpenAiChatModel

    @MockitoBean
    private lateinit var textModel: DeepSeekChatModel

    private val contributors = setOf(ChatClients.VISION, ChatClients.TEXT)

    @BeforeEach
    fun stubChatModels() {
        doReturn(OpenAiChatOptions.builder().build()).whenever(visionModel).options
        doReturn(DeepSeekChatOptions.builder().build()).whenever(textModel).options
        listOf(visionModel, textModel).forEach { model -> stubCall(model) }
    }

    private fun stubCall(model: ChatModel) {
        doReturn(ChatResponse(listOf(Generation(AssistantMessage("Hello")))))
            .whenever(model).call(any<Prompt>())
    }

    @Test
    fun notInDefaultGroup() {
        val response = rest.getForEntity("/actuator/health", Map::class.java)

        val components = components(response.body!!)
        contributors.forEach { name -> assertFalse(components.containsKey(name)) }
        assertTrue(components.containsKey("db"))
        verify(visionModel, never()).call(any<Prompt>())
        verify(textModel, never()).call(any<Prompt>())
    }

    @Test
    fun inLlmGroup() {
        val response = rest.getForEntity("/actuator/health/llm", Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(contributors, components(response.body!!).keys)
        verify(visionModel).call(any<Prompt>())
        verify(textModel).call(any<Prompt>())
    }

    @Suppress("UNCHECKED_CAST")
    private fun components(body: Map<*, *>): Map<String, Any> = body["components"] as Map<String, Any>
}
