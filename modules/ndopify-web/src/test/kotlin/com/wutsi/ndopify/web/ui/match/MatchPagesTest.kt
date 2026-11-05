package com.wutsi.ndopify.web.ui.match

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.web.service.TenantFixtures.TENANT
import java.time.Instant
import java.time.LocalDate
import java.util.Date
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

    private fun date(iso: String) = Date.from(Instant.parse(iso))

    private fun page(
        match: Match = this.match,
        mandate: Mandate = this.mandate,
        agent: Agent = this.agent,
        listing: Listing? = this.listing,
        answer: String? = null,
        customer: Customer? = null,
        openVisit: VisitSummary? = null,
        visit: String? = null,
    ) = MatchPages.of(match, mandate, agent, listing, images, locations, TENANT, FORMATTER, today, answer, customer, openVisit, visit)

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
    fun expired() {
        val page = page(match = match.copy(status = MatchStatus.EXPIRED), answer = "accepted")

        assertEquals(AnswerState.EXPIRED, page.state)
        assertNotNull(page.listing)
        assertNull(page.flash)
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
    fun `agent photo`() {
        val photo = "https://cdn.ndopify.com/agents/3.jpg"

        assertEquals(photo, page(agent = agent.copy(party = agent.party.copy(photoUrl = photo))).agentPhotoUrl)
        assertNull(page(agent = agent.copy(party = agent.party.copy(photoUrl = " "))).agentPhotoUrl)
    }

    @Test
    fun `agent without name nor WhatsApp`() {
        val page = page(agent = Agent(), answer = "accepted", match = match.copy(status = MatchStatus.ACCEPTED))

        assertEquals("Votre agent vous propose ce logement", page.title)
        assertEquals("votre agent", page.agentFirstName)
        assertEquals("Votre agent", page.agentName)
        assertEquals("", page.agentInitials)
        assertNull(page.agentPhotoUrl)
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

    @Test
    fun `accepted - visit form`() {
        val page = page(match = match.copy(status = MatchStatus.ACCEPTED), mandate = mandate.copy(status = MandateStatus.APPROVED),
            customer = Customer(party = Party(firstName = "Sandrine", lastName = "Eboa"), whatsappNumber = "+237699887766"))

        assertEquals(7, page.visit?.form?.days?.size)
        assertEquals("2026-11-01", page.visit?.form?.days?.first()?.value)
        assertEquals(25, page.visit?.form?.times?.size)
        assertEquals("Sandrine Eboa · WhatsApp +237699887766", page.visit?.form?.contact)
        assertEquals(true, page.visit?.form?.video)
        assertNull(page.visit?.open)
    }

    @Test
    fun `accepted - open visit replaces the form`() {
        val page = page(match = match.copy(status = MatchStatus.ACCEPTED), mandate = mandate.copy(status = MandateStatus.APPROVED),
            openVisit = VisitSummary(id = 9, status = VisitStatus.REQUESTED, type = VisitType.VIDEO, requestedAt = date("2026-11-07T09:30:00Z")))

        assertEquals(9L, page.visit?.open?.id)
        assertEquals("Visite vidéo · samedi 7 novembre 2026 à 10:30", page.visit?.open?.slot)
        assertNull(page.visit?.form)
    }

    @Test
    fun `accepted - mandate not approved leaves no form`() {
        val page = page(match = match.copy(status = MatchStatus.ACCEPTED), mandate = mandate.copy(status = MandateStatus.PENDING_APPROVAL))
        assertNull(page.visit?.form)
    }

    @Test
    fun `unavailable listing still shows an open visit`() {
        val page = page(match = match.copy(status = MatchStatus.ACCEPTED), listing = null,
            openVisit = VisitSummary(id = 9, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE))

        assertEquals(AnswerState.UNAVAILABLE, page.state)
        assertEquals(9L, page.visit?.open?.id)
        assertNull(page.visit?.form)
    }

    @Test
    fun `accepted - no whatsapp hides the video option`() {
        val page = page(match = match.copy(status = MatchStatus.ACCEPTED), mandate = mandate.copy(status = MandateStatus.APPROVED),
            customer = Customer(party = Party(firstName = "Sandrine"), whatsappNumber = null))
        assertEquals(false, page.visit?.form?.video)
        assertEquals("Sandrine", page.visit?.form?.contact)
    }

    @Test
    fun `visit flashes`() {
        assertEquals("Votre demande de visite est envoyée. L'agent vous confirme la date par e-mail sous 48 h.",
            page(match = match.copy(status = MatchStatus.ACCEPTED), openVisit = VisitSummary(id = 9), visit = "requested").visitFlash)
        assertEquals("Ce créneau n'est plus disponible. Choisissez-en un autre.", page(visit = "invalid-slot").visitFlash)
        assertEquals("La visite n'a pas pu être demandée. Rechargez la page ou contactez votre agent.", page(visit = "conflict").visitFlash)
        assertNull(page(visit = "requested").visitFlash) // no open visit: nothing happened
    }
}
