package com.wutsi.ndopify.npc.ui.agent

import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.npc.service.TenantFixtures
import com.wutsi.ndopify.npc.ui.agent.AgentProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.agent.AgentProfileFixtures.LISTINGS
import com.wutsi.ndopify.npc.ui.agent.AgentProfileFixtures.LOCATIONS
import com.wutsi.ndopify.npc.ui.agent.AgentProfileFixtures.SPARSE_AGENT
import com.wutsi.ndopify.npc.ui.agent.AgentProfileFixtures.SPARSE_LISTING
import org.springframework.web.util.UriComponentsBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentProfileViewsTest {
    private val formatter = TenantFixtures.FORMATTER
    private val page = AgentListingsPage(listings = LISTINGS, page = 1, pages = 2)
    private val images = mapOf(
        5001L to listOf(ListingImage(id = 9001L, listingId = 5001L, url = "https://cdn.ndopify.com/listing/5001/9001.jpg")),
    )

    @Test
    fun `of - the identity card`() {
        val view = AgentProfileViews.of(AGENT, page, images, LOCATIONS, formatter, null)

        assertEquals(30L, view.id)
        assertEquals("Clarisse Mbarga", view.name)
        assertEquals("CM", view.initials)
        assertEquals("https://cdn.ndopify.com/party/300/photo/a.jpg", view.photoUrl)
        assertTrue(view.verified)
        assertEquals("Indépendant", view.typeLabel)
        assertEquals("Douala", view.city)
        assertEquals("Membre depuis mars 2023", view.since)
        assertEquals("Douala", view.sectorCity)
        assertEquals(listOf("Bonapriso", "Akwa"), view.sectorNeighborhoods)
        assertEquals("Démarcheuse à Douala depuis 3 ans.", view.biography)
        assertEquals("https://wa.me/237699123456", view.whatsappUrl)
        assertFalse(view.listingsUnavailable)
    }

    @Test
    fun `of - the listings`() {
        val view = AgentProfileViews.of(AGENT, page, images, LOCATIONS, formatter, null)

        val card = view.listings.first()
        assertEquals(5001L, card.id)
        assertEquals("Appartement 2 chambres", card.title)
        assertEquals("Bonapriso, Douala", card.place)
        assertEquals("80 m² · Meublé", card.facts)
        assertEquals("250K\u00a0FCFA / mois", card.price)
        assertEquals("https://cdn.ndopify.com/listing/5001/9001.jpg", card.photoUrl)
        assertEquals("/listings/5001", card.url)
        assertEquals(listOf("Appartement 2 chambres", "Studio", "Maison 3 chambres"), view.listings.map { it.title })
    }

    @Test
    fun `of - the hero image falls back to the first, and needs a URL`() {
        val first = mapOf(
            5003L to listOf(
                ListingImage(id = 9003L, listingId = 5003L, url = "https://cdn.ndopify.com/listing/5003/9003.jpg"),
                ListingImage(id = 9004L, listingId = 5003L, url = "https://cdn.ndopify.com/listing/5003/9004.jpg"),
            ),
            5002L to listOf(ListingImage(id = 9002L, listingId = 5002L, url = " ")),
        )
        val view = AgentProfileViews.of(AGENT, page, first, LOCATIONS, formatter, null)

        assertEquals("https://cdn.ndopify.com/listing/5003/9003.jpg", view.listings[2].photoUrl)
        assertNull(view.listings[1].photoUrl)
    }

    @Test
    fun `of - the pager`() {
        val view = AgentProfileViews.of(AGENT, page, images, LOCATIONS, formatter, null)

        assertEquals(1, view.page)
        assertEquals(2, view.pages)
        assertNull(view.prevUrl)
        assertEquals("/agent?id=30&page=2", view.nextUrl)
    }

    @Test
    fun `of - the pager's second page and back`() {
        val view = AgentProfileViews.of(
            AGENT,
            AgentListingsPage(listings = emptyList(), page = 2, pages = 2),
            images,
            LOCATIONS,
            formatter,
            "/listings/501",
        )

        assertEquals("/agent?id=30&page=1&back=/listings/501", view.prevUrl)
        assertNull(view.nextUrl)
        assertEquals(
            "/listings/501",
            UriComponentsBuilder.fromUriString(view.prevUrl!!).build().getQueryParams().getFirst("back"),
        )
    }

    @Test
    fun `of - listings unavailable`() {
        val view = AgentProfileViews.of(AGENT, null, emptyMap(), LOCATIONS, formatter, null)

        assertTrue(view.listingsUnavailable)
        assertEquals(emptyList(), view.listings)
        assertEquals(1, view.page)
        assertEquals(1, view.pages)
        assertNull(view.prevUrl)
        assertNull(view.nextUrl)
    }

    @Test
    fun `of - a sparse agent hides its fields`() {
        val view = AgentProfileViews.of(SPARSE_AGENT, null, emptyMap(), emptyMap(), formatter, null)

        assertEquals("", view.name)
        assertEquals("NP", view.initials)
        assertNull(view.photoUrl)
        assertFalse(view.verified)
        assertNull(view.typeLabel)
        assertNull(view.city)
        assertNull(view.since)
        assertNull(view.sectorCity)
        assertEquals(emptyList(), view.sectorNeighborhoods)
        assertNull(view.biography)
        assertNull(view.whatsappUrl)
    }

    @Test
    fun `of - a sparse listing`() {
        val view = AgentProfileViews.of(
            AGENT,
            AgentListingsPage(listings = listOf(SPARSE_LISTING), page = 1, pages = 1),
            emptyMap(),
            LOCATIONS,
            formatter,
            null,
        )

        val card = view.listings.single()
        assertEquals("Bien", card.title)
        assertNull(card.place)
        assertNull(card.facts)
        assertNull(card.price)
        assertNull(card.photoUrl)
        assertEquals("/listings/5009", card.url)
        assertNull(view.prevUrl)
        assertNull(view.nextUrl)
    }
}
