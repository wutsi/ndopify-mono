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

class OccupancyDocumentDataExtractorTest {
    private val chatModel = mock<ChatModel>()
    private val clock = Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC)
    private val extractor = OccupancyDocumentDataExtractor(ChatClient.builder(chatModel).build(), clock)
    private val images = listOfNotNull(javaClass.getResource("/files/identity/cm/cni-front.png")).map { File(it.file) }

    private val answer = """
        {
            "detectedType": "UTILITY_BILL",
            "holderName": "A",
            "address": "Rue 1, Yaoundé",
            "locality": "Yaoundé",
            "provider": "ENEO",
            "issueDate": "2020-01-01",
            "valid": true
        }
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
        doReturn(ChatResponse(listOf(Generation(AssistantMessage(answer)))))
            .whenever(chatModel).call(any<Prompt>())
    }

    @Test
    fun `types are the three occupancy documents`() {
        assertEquals(
            setOf(DocumentType.LEASE_CONTRACT, DocumentType.UTILITY_BILL, DocumentType.TAX_RECEIPT),
            extractor.types,
        )
    }

    @Test
    fun `extract - a lease contract`() {
        val data = extractor.extract(DocumentType.LEASE_CONTRACT, images)

        assertEquals(LeaseContract::class, data::class)
        assertEquals("A", (data as LeaseContract).holderName)
        assertEquals("Rue 1, Yaoundé", data.address)
        assertEquals("Yaoundé", data.locality)
        assertEquals("ENEO", data.provider)
        assertEquals("2020-01-01", data.issueDate)
        assertEquals("UTILITY_BILL", data.detectedType)
    }

    @Test
    fun `extract - a utility bill`() {
        val data = extractor.extract(DocumentType.UTILITY_BILL, images)

        assertEquals(UtilityBill::class, data::class)
        assertEquals("A", (data as UtilityBill).holderName)
    }

    @Test
    fun `extract - a tax receipt`() {
        val data = extractor.extract(DocumentType.TAX_RECEIPT, images)

        assertEquals(TaxReceipt::class, data::class)
        assertEquals("A", (data as TaxReceipt).holderName)
    }

    @Test
    fun `extract - a null answer throws`() {
        doReturn(ChatResponse(listOf(Generation(AssistantMessage("null")))))
            .whenever(chatModel).call(any<Prompt>())

        assertFailsWith<DocumentExtractionException> { extractor.extract(DocumentType.UTILITY_BILL, images) }
    }
}
