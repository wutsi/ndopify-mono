package com.wutsi.ndopify.npc.ui.agent

import com.wutsi.ndopify.npc.service.TenantFixtures
import com.wutsi.ndopify.npc.ui.agent.AgentProfileFixtures.LISTING_APARTMENT
import com.wutsi.ndopify.npc.ui.agent.AgentProfileFixtures.SPARSE_LISTING
import org.springframework.web.util.UriComponentsBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentProfileRulesTest {
    private val formatter = TenantFixtures.FORMATTER

    @Test
    fun `pages - three per page, at least one`() {
        assertEquals(1, AgentProfileRules.pages(0))
        assertEquals(1, AgentProfileRules.pages(3))
        assertEquals(2, AgentProfileRules.pages(4))
        assertEquals(2, AgentProfileRules.pages(6))
    }

    @Test
    fun `page - clamped to the whole pages`() {
        assertEquals(1, AgentProfileRules.page(6, 0))
        assertEquals(1, AgentProfileRules.page(6, 1))
        assertEquals(2, AgentProfileRules.page(6, 2))
        assertEquals(2, AgentProfileRules.page(6, 99))
    }

    @Test
    fun `pageUrl - id, page and a valid back`() {
        assertEquals("/agent?id=30&page=2", AgentProfileRules.pageUrl(30, 2, null))

        val backUrl = AgentProfileRules.pageUrl(30, 2, "/listings/501")
        assertEquals(
            "/listings/501",
            UriComponentsBuilder.fromUriString(backUrl).build().getQueryParams().getFirst("back"),
        )
    }

    @Test
    fun `back - same-origin paths only`() {
        assertNull(AgentProfileRules.back(null))
        assertNull(AgentProfileRules.back(""))
        assertNull(AgentProfileRules.back("javascript:alert(1)"))
        assertNull(AgentProfileRules.back("//evil.example"))
        assertNull(AgentProfileRules.back("https://evil.example"))
        assertNull(AgentProfileRules.back("/listings\\501"))
        assertEquals("/listings/501", AgentProfileRules.back("/listings/501"))
        assertEquals("/", AgentProfileRules.back("/"))
    }

    @Test
    fun `listingTitle - type and rooms`() {
        assertEquals("Appartement 2 chambres", AgentProfileRules.listingTitle(LISTING_APARTMENT))
        assertEquals("Studio", AgentProfileRules.listingTitle(AgentProfileFixtures.LISTING_STUDIO))
        assertEquals("Maison 3 chambres", AgentProfileRules.listingTitle(AgentProfileFixtures.LISTING_HOUSE))
    }

    @Test
    fun `listingTitle - sparse summary`() {
        assertEquals("Bien", AgentProfileRules.listingTitle(SPARSE_LISTING))
    }

    @Test
    fun `listingPlace - neighbourhood and city`() {
        assertEquals("Bonapriso, Douala", AgentProfileRules.listingPlace(LISTING_APARTMENT, AgentProfileFixtures.LOCATIONS))
        assertNull(AgentProfileRules.listingPlace(SPARSE_LISTING, AgentProfileFixtures.LOCATIONS))
    }

    @Test
    fun `listingFacts - area and furniture`() {
        assertEquals("80 m² · Meublé", AgentProfileRules.listingFacts(LISTING_APARTMENT, formatter))
        assertNull(AgentProfileRules.listingFacts(SPARSE_LISTING, formatter))
    }

    @Test
    fun `listingPrice - compact, monthly`() {
        assertEquals("250K\u00a0FCFA / mois", AgentProfileRules.listingPrice(LISTING_APARTMENT, formatter))
        assertNull(AgentProfileRules.listingPrice(SPARSE_LISTING, formatter))
    }

    @Test
    fun `whatsappUrl - digits only, null without a number`() {
        assertEquals("https://wa.me/237699123456", AgentProfileRules.whatsappUrl("+237699123456"))
        assertNull(AgentProfileRules.whatsappUrl(null))
        assertNull(AgentProfileRules.whatsappUrl(" "))
    }
}
