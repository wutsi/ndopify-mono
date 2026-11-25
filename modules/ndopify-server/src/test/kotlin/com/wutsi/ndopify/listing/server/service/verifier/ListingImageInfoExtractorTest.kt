package com.wutsi.ndopify.listing.server.service.verifier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingImageInfoExtractorTest {
    private val chatModel = mock<ChatModel>()
    private val locationService = mock<LocationService>()
    private val extractor = ListingImageInfoExtractor(ChatClient.builder(chatModel).build(), locationService)
    private val file = File.createTempFile("listing", ".png")
    private val listing = ListingEntity(
        id = 1L,
        listingType = ListingType.SALE,
        propertyType = PropertyType.LAND,
        cityId = 100L,
        area = 1200,
    )

    @BeforeEach
    fun setUp() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
        doReturn(listOf(LocationEntity(id = 100L, name = "Douala"))).whenever(locationService).search(any())
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

    private fun prompt(): String {
        val prompt = argumentCaptor<Prompt>()
        verify(chatModel).call(prompt.capture())
        return prompt.firstValue.instructions.filterIsInstance<UserMessage>().last().text!!
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

        val info = assertNotNull(extractor.extract(file, listing))

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

        val info = assertNotNull(extractor.extract(file, listing))

        assertEquals(false, info.valid)
        assertEquals(2, info.quality)
        assertEquals("Generic map", info.reason)
    }

    @Test
    fun `no response`() {
        doReturn(ChatResponse(emptyList())).whenever(chatModel).call(any<Prompt>())

        val info = extractor.extract(file, listing)

        assertNull(info)
    }

    @Test
    fun `send the prompt and the image`() {
        mockResponse("""{"title": "Salon", "description": "Salon", "valid": true, "quality": 3}""")

        extractor.extract(file, listing)

        val prompt = argumentCaptor<Prompt>()
        verify(chatModel).call(prompt.capture())
        val message = prompt.firstValue.instructions.filterIsInstance<UserMessage>().last()
        assertTrue(message.text!!.contains("real estate agent"))
        assertEquals(1, message.media.size)
        assertEquals("image/png", message.media[0].mimeType.toString())
    }

    @Test
    fun `the prompt describes the listing, and the kind comes back`() {
        mockResponse(
            """
            {"title": "Plan du rez-de-chaussée", "description": "3 pièces", "valid": true, "quality": 4, "kind": "FLOORPLAN"}
            """.trimIndent(),
        )

        val info = assertNotNull(extractor.extract(file, listing))

        assertEquals("FLOORPLAN", info.kind)
        assertEquals("Plan du rez-de-chaussée", info.title)
        assertTrue(prompt().contains("The listing describes: LAND (SALE), lot area 1200 m2, in Douala."))
        assertFalse(prompt().contains("{{"), prompt())
    }

    @Test
    fun `the prompt without the listing details`() {
        doReturn(emptyList<LocationEntity>()).whenever(locationService).search(any())
        mockResponse("""{"title": "Salon", "description": "Salon", "valid": true, "quality": 3}""")

        extractor.extract(file, ListingEntity(id = 1L, listingType = ListingType.RENTAL))

        assertTrue(prompt().contains("The listing describes: - (RENTAL), lot area - m2, in -."))
        assertFalse(prompt().contains("{{"), prompt())
    }

    @Test
    fun `the prompt accepts floor plans and parcel maps, and keeps generic maps out`() {
        mockResponse("""{"title": "Salon", "description": "Salon", "valid": true, "quality": 3}""")

        extractor.extract(file, listing)

        val prompt = prompt()
        assertTrue(prompt.contains("FLOORPLAN"))
        assertTrue(prompt.contains("Parcel maps (land listings only)"))
        assertTrue(prompt.contains("Generic maps"))
        assertTrue(prompt.contains("kind: (PHOTO|FLOORPLAN|MAP)"))
    }
}
