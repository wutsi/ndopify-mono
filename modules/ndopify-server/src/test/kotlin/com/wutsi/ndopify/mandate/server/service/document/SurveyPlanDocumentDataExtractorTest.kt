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

class SurveyPlanDocumentDataExtractorTest {
    private val chatModel = mock<ChatModel>()
    private val clock = Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC)
    private val extractor = SurveyPlanDocumentDataExtractor(ChatClient.builder(chatModel).build(), clock)
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
    fun `types is the survey plan`() {
        assertEquals(setOf(DocumentType.SURVEY_PLAN), extractor.types)
    }

    @Test
    fun `extract - a survey plan`() {
        mockResponse(
            """
            {
                "detectedType": "SURVEY_PLAN",
                "parcelReference": "PB-1",
                "locality": "Yaoundé",
                "surface": "500",
                "coordinates": "3.8480, 11.5021",
                "valid": true
            }
            """.trimIndent(),
        )

        val data = extractor.extract(DocumentType.SURVEY_PLAN, images) as SurveyPlan

        assertEquals("SURVEY_PLAN", data.detectedType)
        assertEquals("PB-1", data.parcelReference)
        assertEquals("Yaoundé", data.locality)
        assertEquals("500", data.surface)
        assertEquals("3.8480, 11.5021", data.coordinates)
        assertEquals(true, data.valid)
    }

    @Test
    fun `extract - a null answer throws`() {
        mockResponse("null")

        assertFailsWith<DocumentExtractionException> { extractor.extract(DocumentType.SURVEY_PLAN, images) }
    }
}
