package com.wutsi.ndopify.listing.server.service.verifier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingImageInfoExtractorTest {
    private val chatModel = mock<ChatModel>()
    private val extractor = ListingImageInfoExtractor(ChatClient.builder(chatModel).build())
    private val file = File.createTempFile("listing", ".png")

    @BeforeEach
    fun setUp() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
        file.writeBytes(byteArrayOf(1, 2, 3))
    }

    @AfterEach
    fun tearDown() {
        file.delete()
    }

    private fun mockResponse(json: String) {
        doReturn(ChatResponse(listOf(Generation(AssistantMessage(json)))))
            .whenever(chatModel).call(any<Prompt>())
    }

    @Test
    fun valid() {
        mockResponse(
            """
            {
                "title": "Salon lumineux",
                "description": "Grand salon avec baies vitrées",
                "valid": true,
                "quality": 4
            }
            """.trimIndent(),
        )

        val info = assertNotNull(extractor.extract(file))

        assertEquals("Salon lumineux", info.title)
        assertEquals("Grand salon avec baies vitrées", info.description)
        assertTrue(info.valid)
        assertEquals(4, info.quality)
        assertNull(info.reason)
    }

    @Test
    fun invalid() {
        mockResponse(
            """
            {
                "title": "Carte",
                "description": "Carte de la ville",
                "valid": false,
                "quality": 2,
                "reason": "Generic map"
            }
            """.trimIndent(),
        )

        val info = assertNotNull(extractor.extract(file))

        assertEquals(false, info.valid)
        assertEquals(2, info.quality)
        assertEquals("Generic map", info.reason)
    }

    @Test
    fun `no response`() {
        doReturn(ChatResponse(emptyList())).whenever(chatModel).call(any<Prompt>())

        val info = extractor.extract(file)

        assertNull(info)
    }

    @Test
    fun `send the prompt and the image`() {
        mockResponse("""{"title": "Salon", "description": "Salon", "valid": true, "quality": 3}""")

        extractor.extract(file)

        val prompt = argumentCaptor<Prompt>()
        verify(chatModel).call(prompt.capture())
        val message = prompt.firstValue.instructions.filterIsInstance<UserMessage>().last()
        assertTrue(message.text!!.contains("real estate agent"))
        assertEquals(1, message.media.size)
        assertEquals("image/png", message.media[0].mimeType.toString())
    }
}
