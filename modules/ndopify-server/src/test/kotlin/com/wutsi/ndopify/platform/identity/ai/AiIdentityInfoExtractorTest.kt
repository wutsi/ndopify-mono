package com.wutsi.ndopify.platform.identity.ai

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.refdata.dto.IdentityStatus
import com.wutsi.ndopify.refdata.dto.IdentityType
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import java.io.File
import kotlin.test.assertEquals

class AiIdentityInfoExtractorTest {
    private val chatModel = mock<ChatModel>()
    private val extractor = AiIdentityInfoExtractor(ChatClient.builder(chatModel))

    init {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
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
                "status": "VALID",
                "invalidityReason": null
            }
            """.trimIndent(),
        )

        val images = listOf(
            AiIdentityInfoExtractorTest::class.java.getResource("/files/identity/cm/cni-front.png")?.file,
            AiIdentityInfoExtractorTest::class.java.getResource("/files/identity/cm/cni-back.png")?.file,
        )
        val info = extractor.extract(images.mapNotNull { image -> File(image) })

        assertEquals("CM", info.countryCode)
        assertEquals("DANIEL CHARLES AUGUSTINE", info.firstName)
        assertEquals("TANDENT YANG AHANDA", info.lastName)
        assertEquals("AA00000000", info.number)
        assertEquals("2032-01-12", info.expiryDate)
        assertEquals(IdentityStatus.VALID, info.status)
        assertEquals(IdentityType.NATIONAL_ID, info.type)
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
                "status": "EXPIRED",
                "invalidityReason": null
            }
            """.trimIndent(),
        )

        val images = listOf(
            AiIdentityInfoExtractorTest::class.java.getResource("/files/identity/cm/passport.webp")?.file,
        )
        val info = extractor.extract(images.mapNotNull { image -> File(image) })

        assertEquals("CM", info.countryCode)
        assertEquals("SAMUEL", info.firstName)
        assertEquals("ETO'O FILS", info.lastName)
        assertEquals("01585140", info.number)
        assertEquals("2023-03-09", info.expiryDate)
        assertEquals(IdentityStatus.EXPIRED, info.status)
        assertEquals(IdentityType.PASSPORT, info.type)
    }
}
