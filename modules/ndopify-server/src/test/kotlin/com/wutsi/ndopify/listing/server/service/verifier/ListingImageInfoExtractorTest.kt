package com.wutsi.ndopify.listing.server.service.verifier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.util.MimeTypeUtils
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingImageInfoExtractorTest {
    private val chatModel = mock<ChatModel>()
    private val extractor = ListingImageInfoExtractor(ChatClient.builder(chatModel).build())

    @TempDir
    lateinit var dir: File

    private lateinit var image: File

    @BeforeEach
    fun setUp() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options

        image = File(dir, "living-room.jpg")
        image.writeBytes(byteArrayOf(1, 2, 3))
    }

    private fun mockResponse(json: String) {
        doReturn(ChatResponse(listOf(Generation(AssistantMessage(json)))))
            .whenever(chatModel).call(any<Prompt>())
    }

    @Test
    fun `valid image`() {
        mockResponse(
            """
            {
                "title": "Salon lumineux",
                "description": "Grand salon avec baie vitrée",
                "valid": true
            }
            """.trimIndent(),
        )

        val info = extractor.extract(image)

        assertEquals("Salon lumineux", info.title)
        assertEquals("Grand salon avec baie vitrée", info.description)
        assertTrue(info.valid)
        assertNull(info.reason)
    }

    @Test
    fun `invalid image`() {
        mockResponse(
            """
            {
                "title": "Selfie",
                "description": "Photo d'une personne",
                "valid": false,
                "reason": "The picture does not show the property"
            }
            """.trimIndent(),
        )

        val info = extractor.extract(image)

        assertEquals("Selfie", info.title)
        assertEquals("Photo d'une personne", info.description)
        assertFalse(info.valid)
        assertEquals("The picture does not show the property", info.reason)
    }

    @Test
    fun `prompt contains instructions and the image`() {
        mockResponse("""{"title": "Cuisine", "description": "Cuisine équipée", "valid": true}""")

        extractor.extract(image)

        val captor = argumentCaptor<Prompt>()
        verify(chatModel).call(captor.capture())
        val message = captor.firstValue.userMessage

        assertTrue(message.text!!.contains("real estate agent"))
        assertEquals(1, message.media.size)
        assertEquals(MimeTypeUtils.IMAGE_JPEG, message.media[0].mimeType)
    }

    @Test
    fun `null response - default info`() {
        mockResponse("null")

        val info = extractor.extract(image)

        assertEquals(ListingImageInfo(), info)
    }
}
