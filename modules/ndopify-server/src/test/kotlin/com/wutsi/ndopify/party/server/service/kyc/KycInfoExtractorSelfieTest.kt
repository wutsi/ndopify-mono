package com.wutsi.ndopify.party.server.service.kyc

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.party.dto.IdentificationType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

class KycInfoExtractorSelfieTest {
    private val chatModel = mock<ChatModel>()
    private val extractor = KycInfoExtractorSelfie(ChatClient.builder(chatModel))

    private val images = listOf(
        "/files/identity/cm/cni-front.png",
        "/files/identity/us/driver-license-front.jpg",
    ).map { path -> File(KycInfoExtractorSelfieTest::class.java.getResource(path)!!.file) }

    @BeforeEach
    fun setUp() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
    }

    private fun mockResponse(json: String) {
        doReturn(ChatResponse(listOf(Generation(AssistantMessage(json)))))
            .whenever(chatModel).call(any<Prompt>())
    }

    @Test
    fun `selfie matches identity document`() {
        mockResponse(
            """
            {
                "type": "NATIONAL_ID",
                "valid": true,
                "confidence": 0.95
            }
            """.trimIndent(),
        )

        val info = extractor.extract(images)

        assertEquals(IdentificationType.NATIONAL_ID, info.type)
        assertTrue(info.valid)
        assertNull(info.invalidityReason)
        assertEquals(0.95, info.confidence)
    }

    @Test
    fun `selfie does not match identity document`() {
        mockResponse(
            """
            {
                "type": "PASSPORT",
                "valid": false,
                "invalidityReason": "Selfie does not match the document",
                "confidence": 0.8
            }
            """.trimIndent(),
        )

        val info = extractor.extract(images)

        assertEquals(IdentificationType.PASSPORT, info.type)
        assertFalse(info.valid)
        assertEquals("Selfie does not match the document", info.invalidityReason)
        assertEquals(0.8, info.confidence)
    }

    @Test
    fun `prompt contains instructions and every image`() {
        mockResponse("""{"type": "NATIONAL_ID", "valid": true, "confidence": 1.0}""")

        extractor.extract(images)

        val captor = argumentCaptor<Prompt>()
        verify(chatModel).call(captor.capture())
        val message = captor.firstValue.userMessage

        val text = message.text!!
        assertTrue(text.contains("selfie verification"))
        assertFalse(text.contains("{{ID_TYPES}}"))
        IdentificationType.entries.forEach { type -> assertTrue(text.contains(type.name)) }

        assertEquals(2, message.media.size)
        assertEquals(MimeTypeUtils.IMAGE_PNG, message.media[0].mimeType)
        assertEquals(MimeTypeUtils.IMAGE_JPEG, message.media[1].mimeType)
    }

    @Test
    fun `null response - default info`() {
        mockResponse("null")

        val info = extractor.extract(images)

        assertEquals(KycInfo(), info)
    }
}
