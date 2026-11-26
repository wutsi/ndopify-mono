package com.wutsi.ndopify.mandate.server.service.document

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.dto.DocumentType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LandTitleDocumentDataExtractorTest {
    private val chatModel = mock<ChatModel>()
    private val clock = Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC)
    private val extractor = LandTitleDocumentDataExtractor(ChatClient.builder(chatModel).build(), clock)
    private val images = listOfNotNull(javaClass.getResource("/files/identity/cm/cni-front.png")).map { File(it.file) }

    @BeforeEach
    fun setUp() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
    }

    private fun mockResponse(json: String) {
        doReturn(ChatResponse(listOf(Generation(AssistantMessage(json)))))
            .whenever(chatModel).call(any<Prompt>())
    }

    @Test
    fun `types are the two proofs of ownership`() {
        assertEquals(
            setOf(DocumentType.LAND_TITLE, DocumentType.CERTIFICATE_OF_OWNERSHIP),
            extractor.types,
        )
    }

    @Test
    fun `extract - a land title`() {
        mockResponse(
            """
            {
                "detectedType": "LAND_TITLE",
                "titleNumber": "TF-1",
                "owners": ["A", "B"],
                "department": "Centre",
                "arrondissement": "Mfoundi",
                "locality": "Yaoundé",
                "surface": "500",
                "issueDate": "2020-01-01",
                "valid": true,
                "confidence": 0.8,
                "fieldConfidences": {"titleNumber": 0.9, "owners": 0.8, "locality": 0.85}
            }
            """.trimIndent(),
        )

        val data = extractor.extract(DocumentType.LAND_TITLE, images) as LandTitle

        assertEquals("LAND_TITLE", data.detectedType)
        assertEquals("TF-1", data.titleNumber)
        assertEquals(listOf("A", "B"), data.owners)
        assertEquals("Centre", data.department)
        assertEquals("Mfoundi", data.arrondissement)
        assertEquals("Yaoundé", data.locality)
        assertEquals("500", data.surface)
        assertEquals("2020-01-01", data.issueDate)
        assertEquals(true, data.valid)
        assertEquals(0.8, data.confidence)
        assertEquals(0.9, data.fieldConfidences?.get("titleNumber"))
    }

    @Test
    fun `extract - a certificate of ownership`() {
        mockResponse("""{"detectedType":"CERTIFICATE_OF_OWNERSHIP","titleNumber":"CO-1","valid":true}""")

        val data = extractor.extract(DocumentType.CERTIFICATE_OF_OWNERSHIP, images)

        assertEquals(CertificateOfOwnership::class, data::class)
        assertEquals("CERTIFICATE_OF_OWNERSHIP", data.detectedType)
    }

    @Test
    fun `extract - a null answer throws`() {
        mockResponse("null")

        assertFailsWith<DocumentExtractionException> { extractor.extract(DocumentType.LAND_TITLE, images) }
    }
}
