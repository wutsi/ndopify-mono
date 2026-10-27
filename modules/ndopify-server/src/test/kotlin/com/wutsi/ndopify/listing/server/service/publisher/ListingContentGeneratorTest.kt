package com.wutsi.ndopify.listing.server.service.publisher

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingContentGeneratorTest {
    private val chatModel = mock<ChatModel>()
    private val locationService = mock<LocationService>()
    private val imageDao = mock<ListingImageRepository>()
    private val generator = ListingContentGenerator(ChatClient.builder(chatModel).build(), locationService, imageDao)

    private val listing = ListingEntity(
        id = 1L,
        listingType = ListingType.RENTAL,
        propertyType = PropertyType.APARTMENT,
        furnitureType = FurnitureType.FULLY_FURNISHED,
        cityId = 100L,
        neighborhoodId = 200L,
        rooms = 3,
        bathrooms = 2,
        livingRooms = 1,
        kitchens = 1,
        parkings = 2,
        area = 120,
        waterTank = true,
        generatorSet = true,
        securityGuard = true,
    )

    @BeforeEach
    fun setUp() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
        doReturn(
            listOf(
                LocationEntity(id = 100L, name = "Douala"),
                LocationEntity(id = 200L, name = "Bonapriso"),
            ),
        ).whenever(locationService).search(any())
        doReturn(
            listOf(
                ListingImageEntity(id = 11L, listingId = 1L, title = "Salon", description = "Grand salon", quality = 4),
                ListingImageEntity(id = 12L, listingId = 1L, quality = 2),
            ),
        ).whenever(imageDao).findByListingIdAndStatusInOrderById(any(), any())
        mockResponse(
            """
            {
                "title": "Appartement meublé à louer, 3 chambres, Douala (Bonapriso)",
                "summary": "Bel appartement meublé à Bonapriso",
                "description": "Un appartement spacieux.",
                "heroImageId": 11
            }
            """.trimIndent(),
        )
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
    fun generate() {
        val content = generator.generate(listing)

        assertEquals("Appartement meublé à louer, 3 chambres, Douala (Bonapriso)", content?.title)
        assertEquals("Bel appartement meublé à Bonapriso", content?.summary)
        assertEquals("Un appartement spacieux.", content?.description)
        assertEquals(11L, content?.heroImageId)
    }

    @Test
    fun `prompt has the property details`() {
        generator.generate(listing)

        val prompt = prompt()
        assertTrue(prompt.contains("- Type of listing: RENTAL"))
        assertTrue(prompt.contains("- Type of property: APARTMENT"))
        assertTrue(prompt.contains("- Bedrooms: 3"))
        assertTrue(prompt.contains("- Bathrooms: 2"))
        assertTrue(prompt.contains("- Living Rooms: 1"))
        assertTrue(prompt.contains("- Kitchens: 1"))
        assertTrue(prompt.contains("- Lot area (m2): 120"))
        assertTrue(prompt.contains("- Parkings: 2"))
        assertTrue(prompt.contains("- Furnished: FULLY_FURNISHED"))
        assertTrue(prompt.contains("- Water Tank: Yes"))
        assertTrue(prompt.contains("- Water Well: No"))
        assertTrue(prompt.contains("- Generator Set: Yes"))
        assertTrue(prompt.contains("- Security Guard: Yes"))
        assertTrue(prompt.contains("- Video Surveillance: No"))
        assertTrue(prompt.contains("- City: Douala"))
        assertTrue(prompt.contains("- Neighborhood: Bonapriso"))

        // Every placeholder is replaced
        assertFalse(prompt.contains("{{"), prompt)
    }

    @Test
    fun `prompt has the verified images`() {
        generator.generate(listing)

        val prompt = prompt()
        assertTrue(prompt.contains("## Image #1\n\n- ID: 11\n- Title: Salon\n- Quality: 4\n- Description: Grand salon"))
        assertTrue(prompt.contains("## Image #2\n\n- ID: 12\n- Title: No title available\n- Quality: 2"))
        verify(imageDao).findByListingIdAndStatusInOrderById(eq(1L), eq(listOf(ImageStatus.VERIFIED)))
    }

    @Test
    fun `locations of the listing`() {
        generator.generate(listing)

        val request = argumentCaptor<SearchLocationRequest>()
        verify(locationService).search(request.capture())
        assertEquals(listOf(100L, 200L), request.firstValue.ids)
    }

    @Test
    fun `missing details`() {
        doReturn(emptyList<LocationEntity>()).whenever(locationService).search(any())
        doReturn(emptyList<ListingImageEntity>()).whenever(imageDao).findByListingIdAndStatusInOrderById(any(), any())

        generator.generate(ListingEntity(id = 1L, listingType = ListingType.SALE, cityId = 100L))

        val prompt = prompt()
        assertTrue(prompt.contains("- Type of property: -"))
        assertTrue(prompt.contains("- Bedrooms: -"))
        assertTrue(prompt.contains("- Parkings: -"))
        assertTrue(prompt.contains("- Furnished: -"))
        assertTrue(prompt.contains("- City: -"))
        assertTrue(prompt.contains("- Neighborhood: -"))
        assertFalse(prompt.contains("{{"), prompt)
    }

    @Test
    fun `no hero image`() {
        mockResponse("""{"title": "Terrain à vendre, 1200m2, Douala (Bonapriso)"}""")

        val content = generator.generate(listing)

        assertEquals("Terrain à vendre, 1200m2, Douala (Bonapriso)", content?.title)
        assertNull(content?.heroImageId)
    }
}
