package com.wutsi.ndopify.party.server.service.identification

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.TimeZone
import kotlin.test.assertEquals

class IdentificationInfoExtractorAiTest {
    private val chatModel = mock<ChatModel>()
    private val extractor = IdentificationInfoExtractorAi(ChatClient.builder(chatModel))
    private val fmt = SimpleDateFormat("yyyy-MM-dd")

    @BeforeEach
    fun setUp() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
        fmt.timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun mockResponse(json: String) {
        doReturn(ChatResponse(listOf(Generation(AssistantMessage(json)))))
            .whenever(chatModel).call(any<Prompt>())
    }

    @Test
    fun `national_id - CM`() {
        mockResponse(
            """
            {
                "type": "NATIONAL_ID",
                "number": "AA00000000",
                "firstName": "DANIEL CHARLES AUGUSTINE",
                "lastName": "TANDENT YANG AHANDA",
                "countryCode": "CM",
                "expiryDate": "2032-01-12",
                "valid": true
            }
            """.trimIndent(),
        )

        val images = listOf(
            IdentificationInfoExtractorAiTest::class.java.getResource("/files/identity/cm/cni-front.png")?.file,
            IdentificationInfoExtractorAiTest::class.java.getResource("/files/identity/cm/cni-back.png")?.file,
        )
        val info = extractor.extract(images.mapNotNull { image -> File(image) })

        assertEquals("CM", info.countryCode)
        assertEquals("DANIEL CHARLES AUGUSTINE", info.firstName)
        assertEquals("TANDENT YANG AHANDA", info.lastName)
        assertEquals("AA00000000", info.number)
        assertEquals(fmt.parse("2032-01-12"), info.expiryDate)
        assertEquals(true, info.valid)
        assertEquals(IdentificationType.NATIONAL_ID, info.type)
    }

    @Test
    fun `passport - CM`() {
        mockResponse(
            """
            {
                "type": "PASSPORT",
                "number": "01585140",
                "firstName": "SAMUEL",
                "lastName": "ETO'O FILS",
                "countryCode": "CM",
                "expiryDate": "2023-03-09",
                "valid": true
            }
            """.trimIndent(),
        )

        val images = listOf(
            IdentificationInfoExtractorAiTest::class.java.getResource("/files/identity/cm/passport.webp")?.file,
        )
        val info = extractor.extract(images.mapNotNull { image -> File(image) })

        assertEquals("CM", info.countryCode)
        assertEquals("SAMUEL", info.firstName)
        assertEquals("ETO'O FILS", info.lastName)
        assertEquals("01585140", info.number)
        assertEquals(fmt.parse("2023-03-09"), info.expiryDate)
        assertEquals(true, info.valid)
        assertEquals(IdentificationType.PASSPORT, info.type)
    }

    @Test
    fun `driver_license - US`() {
        mockResponse(
            """
            {
                "type": "DRIVER_LICENSE",
                "number": "D1234567",
                "firstName": "JOHN",
                "lastName": "DOE",
                "countryCode": "US",
                "expiryDate": "2025-12-31",
                "valid": false,
                "invalidityReason": "Document is forged!"
            }
            """.trimIndent(),
        )

        val images = listOf(
            IdentificationInfoExtractorAiTest::class.java.getResource("/files/identity/us/driver-license-front.jpg")?.file,
        )
        val info = extractor.extract(images.mapNotNull { image -> File(image) })

        assertEquals("US", info.countryCode)
        assertEquals("JOHN", info.firstName)
        assertEquals("DOE", info.lastName)
        assertEquals("D1234567", info.number)
        assertEquals(fmt.parse("2025-12-31"), info.expiryDate)
        assertEquals(false, info.valid)
        assertEquals(IdentificationType.DRIVER_LICENSE, info.type)
    }
}
