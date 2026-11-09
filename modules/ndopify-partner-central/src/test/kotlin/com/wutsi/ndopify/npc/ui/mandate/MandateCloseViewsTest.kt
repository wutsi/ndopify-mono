package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.match.MatchFixtures
import com.wutsi.ndopify.party.dto.Party
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateCloseViewsTest {
    private fun date(iso: String): Date = Date.from(Instant.parse(iso))

    private val answered = date("2026-10-29T10:00:00Z")

    private val listingMatch = Match(
        id = 9001,
        mandateId = 204,
        listingId = 3012,
        status = MatchStatus.ACCEPTED,
        answeredAt = answered,
    )

    private val clientMatch = Match(
        id = 9002,
        mandateId = 204,
        listingId = 501,
        status = MatchStatus.ACCEPTED,
        answeredAt = answered,
    )

    @Test
    fun `a search's candidate - the listing's card`() {
        val view = MandateCloseViews.candidate(
            listingMatch,
            MatchFixtures.LISTING_A,
            MatchFixtures.LOCATIONS,
            FORMATTER,
        )

        assertEquals(9001L, view.matchId)
        assertEquals("Appartement · 3 chambres", view.title)
        assertEquals("annonce n° 3012", view.reference)
        assertEquals("Bonapriso", view.neighbourhood)
        assertEquals("240K\u00a0FCFA / mois", view.rent)
        assertEquals(240000L, view.priceAmount)
        assertEquals("Intéressé le 29 oct. 2026", view.interestLabel)
    }

    @Test
    fun `a listing's candidate - the client's name`() {
        val view = MandateCloseViews.candidate(
            clientMatch,
            ListingViewsTest.SEARCHES[0],
            ListingViewsTest.CUSTOMERS[0],
            listingPrice = 150000,
            FORMATTER,
        )

        assertEquals(9002L, view.matchId)
        assertEquals("Sandrine Eboa", view.title)
        assertNull(view.reference)
        assertNull(view.neighbourhood)
        assertNull(view.rent)
        assertEquals(150000L, view.priceAmount)
        assertEquals("Intéressé le 29 oct. 2026", view.interestLabel)
    }

    @Test
    fun `a listing's candidate - without the customer's name, its mandate's number`() {
        val view = MandateCloseViews.candidate(clientMatch, ListingViewsTest.SEARCHES[0], null, 150000, FORMATTER)

        assertEquals("Mandat n° 204", view.title)
        assertEquals("Intéressé le 29 oct. 2026", view.interestLabel)
    }

    @Test
    fun `a search's candidate - no neighbourhood name shows the city`() {
        val listing = MatchFixtures.LISTING_A.copy(neighborhoodId = null)
        val view = MandateCloseViews.candidate(listingMatch, listing, MatchFixtures.LOCATIONS, FORMATTER)

        assertEquals("Douala", view.neighbourhood)
    }

    @Test
    fun `interest label without an answer`() {
        assertNull(MandateCloseViews.interestLabel(listingMatch.copy(answeredAt = null), FORMATTER))
    }

    @Test
    fun `steps - counterpart for a search, omitted when it doesn't apply`() {
        assertEquals(
            listOf("counterpart" to "Bien", "price" to "Loyer", "confirm" to "Confirmation"),
            MandateCloseViews.steps(true, owner = false).map { it.name to it.label },
        )
        assertEquals(
            listOf("price" to "Loyer", "confirm" to "Confirmation"),
            MandateCloseViews.steps(false, owner = false).map { it.name to it.label },
        )
    }

    @Test
    fun `steps - counterpart for a listing is the client`() {
        assertEquals("Client", MandateCloseViews.steps(true, owner = true).first().label)
    }

    @Test
    fun `outside-only list - no counterpart step, the price is the first`() {
        val steps = MandateCloseViews.steps(counterpartApplicable = false, owner = false)
        val view = MandateCloseViews.of(
            MANDATE.copy(status = MandateStatus.APPROVED),
            emptyList(),
            candidatesUnavailable = false,
            step = "price",
            steps = steps,
            currency = "FCFA",
        )

        assertEquals(201L, view.mandateId)
        assertEquals("Locataire · Maison", view.title)
        assertFalse(view.owner)
        assertTrue(view.candidates.isEmpty())
        assertFalse(view.candidatesUnavailable)
        assertEquals("price", view.step)
        assertEquals(1, view.stepNumber)
        assertEquals(2, view.stepCount)
        assertEquals("FCFA", view.currency)
    }

    @Test
    fun `a listing's page view is owned`() {
        val view = MandateCloseViews.of(
            MANDATE.copy(status = MandateStatus.APPROVED, mandateType = MandateType.LISTING),
            emptyList(),
            candidatesUnavailable = true,
            step = "counterpart",
            steps = MandateCloseViews.steps(true, owner = true),
            currency = "FCFA",
        )

        assertEquals("Propriétaire · Maison", view.title)
        assertTrue(view.owner)
        assertTrue(view.candidatesUnavailable)
    }

    @Test
    fun `a customer with a blank name falls back to the mandate's number`() {
        val blank = CustomerSummary(id = 14, party = Party(firstName = "", lastName = " "))
        val view = MandateCloseViews.candidate(clientMatch, ListingViewsTest.SEARCHES[0], blank, null, FORMATTER)

        assertEquals("Mandat n° 204", view.title)
    }

    @Test
    fun `recap - a search's chosen listing, its customer and the agreed rent`() {
        val selected = MandateCloseViews.candidate(listingMatch, MatchFixtures.LISTING_A, MatchFixtures.LOCATIONS, FORMATTER)

        val recap = MandateCloseViews.recap(
            MANDATE,
            selected,
            outside = false,
            listing = null,
            customer = Party(firstName = "Ray", lastName = "Sponsible"),
            locations = MatchFixtures.LOCATIONS,
            amount = 240000,
            formatter = FORMATTER,
        )

        assertEquals("Appartement · 3 chambres, Bonapriso — annonce n° 3012", recap.property)
        assertEquals("Ray Sponsible", recap.client)
        assertEquals("240\u00a0000\u00a0FCFA / mois", recap.rent)
        assertEquals(
            "À la confirmation : le mandat n° 201 est fermé, l'annonce est retirée du marché et le mandat du bailleur est fermé. Cette action est définitive.",
            recap.note,
        )
    }

    @Test
    fun `recap - a landlord's listing and, outside, no client of the searches`() {
        val listing = ListingViewsTest.COMPLETE.copy(cityId = 100, neighborhoodId = 1001)

        val recap = MandateCloseViews.recap(
            MANDATE.copy(mandateType = MandateType.LISTING),
            selected = null,
            outside = true,
            listing = listing,
            customer = null,
            locations = MatchFixtures.LOCATIONS,
            amount = 150000,
            formatter = FORMATTER,
        )

        assertEquals("Appartement · 2 chambres, Bonapriso — annonce n° 501", recap.property)
        assertEquals("Un client hors de vos recherches", recap.client)
        assertEquals("150\u00a0000\u00a0FCFA / mois", recap.rent)
        assertEquals(
            "À la confirmation : le mandat n° 201 est fermé et l'annonce est retirée du marché. Cette action est définitive.",
            recap.note,
        )
    }

    @Test
    fun `recap - a search without candidates closes only the mandate`() {
        val recap = MandateCloseViews.recap(
            MANDATE,
            selected = null,
            outside = false,
            listing = null,
            customer = Party(firstName = "Ray", lastName = "Sponsible"),
            locations = emptyMap(),
            amount = 240000,
            formatter = FORMATTER,
        )

        assertEquals("Un bien", recap.property)
        assertEquals("Ray Sponsible", recap.client)
        assertEquals("240\u00a0000\u00a0FCFA / mois", recap.rent)
        assertEquals(
            "À la confirmation : le mandat n° 201 est fermé. Aucune annonce n'est retirée du marché. Cette action est définitive.",
            recap.note,
        )
    }
}
