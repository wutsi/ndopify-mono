package com.wutsi.ndopify.web.ui.match

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.web.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.web.service.TenantFixtures.TENANT
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchPagesTest {
    private val today = LocalDate.of(2026, 10, 31)
    private val locations = listOf(
        Location(id = 1, name = "Douala"),
        Location(id = 5, name = "Bonapriso"),
        Location(id = 6, name = "Akwa"),
        Location(id = 7, name = "Bonanjo"),
    )

    private val match = Match(
        id = 12,
        mandateId = 7,
        listingId = 123,
        status = MatchStatus.PROPOSED,
        neighborhoodScore = 1.0,
        priceScore = 1.0,
        roomsScore = 1.0,
        furnitureScore = 1.0,
        availabilityScore = 1.0,
        amenitiesScore = 0.66,
        distanceKm = 0.0,
        missingAmenities = listOf("generatorSet"),
    )

    private val mandate = Mandate(
        id = 7,
        agentId = 3,
        cityId = 1,
        neighborhoodIds = listOf(5),
        propertyType = PropertyType.APARTMENT,
        furnitureType = FurnitureType.UNFURNISHED,
        rooms = "3",
        securedFenced = true,
        waterTank = true,
        generatorSet = true,
        price = Money(250000, "XAF", PeriodicityType.MONTHLY),
        movingPeriod = MovingPeriod.WITHIN_3_MONTHS,
    )

    private val agent = Agent(
        id = 3,
        party = Party(firstName = "Serge", lastName = "Ngono"),
        whatsappNumber = "+237 677 00 01 11",
    )

    private val listing = Listing(
        id = 123,
        status = ListingStatus.PUBLISHED,
        cityId = 1,
        neighborhoodId = 5,
        propertyType = PropertyType.APARTMENT,
        furnitureType = FurnitureType.UNFURNISHED,
        rooms = 3,
        price = 240000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
        availableFrom = LocalDate.of(2026, 12, 1),
        title = "Appartement 3 chambres à Bonapriso",
        agentRemarks = "Propriétaire pressé",
    )

    private val images = listOf(ListingImage(id = 1, url = "https://cdn/1.jpg"))

    private fun page(
        match: Match = this.match,
        mandate: Mandate = this.mandate,
        agent: Agent = this.agent,
        listing: Listing? = this.listing,
        answer: String? = null,
    ) = MatchPages.of(match, mandate, agent, listing, images, locations, TENANT, FORMATTER, today, answer)

    @Test
    fun pending() {
        val page = page()

        assertEquals(12, page.id)
        assertEquals(AnswerState.PENDING, page.state)
        assertEquals("Serge vous propose ce logement", page.title)
        assertEquals("Serge", page.agentFirstName)
        assertEquals("Serge Ngono", page.agentName)
        assertEquals("SN", page.agentInitials)
        assertEquals("https://wa.me/237677000111", page.agentWhatsappUrl)
        assertEquals(
            "Pour votre recherche : appartement 3 chambres à Bonapriso, jusqu'à 250K\u00A0FCFA / mois.",
            page.searchSummary,
        )
        assertEquals("Appartement 3 chambres à Bonapriso", page.listing?.title)
        assertEquals("240K\u00A0FCFA / mois", page.listing?.price)
        assertEquals(1, page.listing?.photos?.size)
        assertNull(page.flash)
    }

    @Test
    fun `why this listing`() {
        val reasons = page().reasons

        assertEquals(
            listOf(
                "À Bonapriso, le quartier que vous cherchez",
                "240K\u00A0FCFA / mois, dans votre budget",
                "3 chambres, comme demandé",
                "Non meublé, comme demandé",
                "Disponible le 01 déc. 2026, avant votre emménagement",
                "Réservoir d'eau, clôturé et sécurisé, mais sans groupe électrogène",
            ),
            reasons.map { it.text },
        )
        assertEquals(listOf(false, false, false, false, false, true), reasons.map { it.partial })
    }

    @Test
    fun `why this listing - only the criteria fully met and searched`() {
        val reasons = page(
            match = match.copy(distanceKm = 1.2, priceScore = 0.8, roomsScore = 0.7, missingAmenities = emptyList()),
            mandate = mandate.copy(furnitureType = null),
            listing = listing.copy(availableFrom = null),
        ).reasons

        assertEquals(listOf("Disponible immédiatement"), reasons.map { it.text })
    }

    @Test
    fun `why this listing - nothing to say`() {
        val reasons = page(
            match = match.copy(distanceKm = null, availabilityScore = 0.5, missingAmenities = emptyList()),
            mandate = mandate.copy(furnitureType = null, rooms = null, price = Money(), movingPeriod = MovingPeriod.UNKNOWN),
        ).reasons

        assertTrue(reasons.isEmpty())
    }

    @Test
    fun `missing amenities only`() {
        val reasons = page(
            mandate = mandate.copy(securedFenced = false, waterTank = false, waterWell = true),
            match = match.copy(missingAmenities = listOf("generatorSet", "waterWell")),
        ).reasons

        assertEquals("Sans forage ni groupe électrogène", reasons.last().text)
    }

    @Test
    fun accepted() {
        val page = page(match = match.copy(status = MatchStatus.ACCEPTED), answer = "accepted")

        assertEquals(AnswerState.ACCEPTED, page.state)
        assertEquals("C'est noté : ce logement vous intéresse.", page.flash)
    }

    @Test
    fun rejected() {
        val page = page(match = match.copy(status = MatchStatus.REJECTED), answer = "rejected")

        assertEquals(AnswerState.REJECTED, page.state)
        assertEquals("C'est noté : vous avez écarté ce logement.", page.flash)
    }

    @Test
    fun `no flash for another state than the answer`() {
        assertNull(page(answer = "accepted").flash)
        assertNull(page(match = match.copy(status = MatchStatus.REJECTED), answer = "accepted").flash)
        assertNull(page(match = match.copy(status = MatchStatus.ACCEPTED), answer = "x").flash)
    }

    @Test
    fun `unavailable - listing not readable`() {
        val page = page(listing = null)

        assertEquals(AnswerState.UNAVAILABLE, page.state)
        assertNull(page.listing)
        assertTrue(page.reasons.isEmpty())
        assertEquals("Serge vous propose ce logement", page.title)
    }

    @Test
    fun `unavailable - listing no longer published, whatever the answer`() {
        val page = page(match = match.copy(status = MatchStatus.ACCEPTED), listing = listing.copy(status = ListingStatus.RENTED))

        assertEquals(AnswerState.UNAVAILABLE, page.state)
        assertNull(page.listing)
    }

    @Test
    fun `unavailable - match not answerable`() {
        assertEquals(AnswerState.UNAVAILABLE, page(match = match.copy(status = MatchStatus.DISMISSED)).state)
    }

    @Test
    fun `search summary - several neighbourhoods, rooms or more, no budget`() {
        val page = page(mandate = mandate.copy(neighborhoodIds = listOf(5, 6, 7), rooms = "5+", price = Money()))

        assertEquals("Pour votre recherche : appartement 5 chambres et + à Bonapriso, Akwa ou Bonanjo.", page.searchSummary)
    }

    @Test
    fun `search summary - the city without neighbourhood, a room, no type`() {
        val page = page(mandate = mandate.copy(neighborhoodIds = emptyList(), rooms = "1", propertyType = null))

        assertEquals("Pour votre recherche : logement 1 chambre à Douala, jusqu'à 250K\u00A0FCFA / mois.", page.searchSummary)
    }

    @Test
    fun `search summary - rooms not a number`() {
        val page = page(mandate = mandate.copy(rooms = "beaucoup", price = Money(250000, "", PeriodicityType.UNKNOWN)))

        assertEquals("Pour votre recherche : appartement à Bonapriso, jusqu'à 250K\u00A0FCFA.", page.searchSummary)
    }

    @Test
    fun `agent without name nor WhatsApp`() {
        val page = page(agent = Agent(), answer = "accepted", match = match.copy(status = MatchStatus.ACCEPTED))

        assertEquals("Votre agent vous propose ce logement", page.title)
        assertEquals("votre agent", page.agentFirstName)
        assertEquals("Votre agent", page.agentName)
        assertEquals("", page.agentInitials)
        assertNull(page.agentWhatsappUrl)
        assertEquals("C'est noté : ce logement vous intéresse.", page.flash)
    }

    @Test
    fun `agent's WhatsApp number without digit`() {
        assertNull(page(agent = agent.copy(whatsappNumber = "-")).agentWhatsappUrl)
    }

    @Test
    fun `never the agent's remarks`() {
        assertNotNull(page().listing)
        assertTrue(!page().toString().contains("Propriétaire pressé"))
    }
}
