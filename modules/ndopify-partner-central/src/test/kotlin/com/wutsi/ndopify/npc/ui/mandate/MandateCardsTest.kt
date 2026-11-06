package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.listing.dto.ListingStats
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStats
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.npc.ui.listing.ListingLineView
import com.wutsi.ndopify.npc.ui.listing.ListingRules
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AKWA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.LOCATIONS
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MandateCardsTest {
    companion object {
        val NOW: Long = Instant.parse("2026-10-18T10:00:00Z").toEpochMilli()
        val NAMES = LOCATIONS.associateBy { it.id }

        /** An ASSIGNED search of the agent of ProfileFixtures. */
        val MANDATE = Mandate(
            id = 201,
            customerId = 555,
            agentId = 20,
            status = MandateStatus.ASSIGNED,
            mandateType = MandateType.SEARCH,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.HOUSE,
            cityId = DOUALA,
            neighborhoodIds = listOf(BONAPRISO, AKWA),
            rooms = "3",
            price = Money(250000, "XAF", PeriodicityType.MONTHLY),
            movingPeriod = MovingPeriod.WITHIN_3_MONTHS,
            comments = "Près d'une école",
        )

        /** The mandate's contract, shared with the mandate page tests. */
        val CONTRACT = Contract(
            id = "c-201",
            signatureRequestedAt = Date.from(Instant.parse("2026-10-15T10:00:00Z")),
            signatureDeadline = Date.from(Instant.parse("2026-10-22T10:00:00Z")),
            validUntil = Date.from(Instant.parse("2027-03-28T10:00:00Z")),
        )
    }

    private fun card(
        mandate: Mandate = MANDATE,
        listings: Map<Long, ListingSummary>? = emptyMap(),
        mandateStats: Map<Long, MandateStats>? = null,
        stats: Map<Long, ListingStats>? = null,
    ) = MandateCards.of(mandate, listings, NAMES, FORMATTER, mandateStats, stats)

    @Test
    fun assigned() {
        val card = card()

        assertEquals(201L, card.id)
        assertEquals("Locataire · Maison", card.title)
        assertEquals(MandateRules.Tag("Assigné", "info"), card.tag)
        assertEquals(
            listOf(
                "Secteurs" to "Bonapriso, Akwa",
                "Budget" to "250K\u00a0FCFA / mois",
                "Emménagement" to "Dans 1 à 3\u00a0mois",
            ),
            card.facts,
        )
    }

    @Test
    fun `pending approval`() {
        val card = card(mandate = MANDATE.copy(status = MandateStatus.PENDING_APPROVAL))

        assertEquals(MandateRules.Tag("En approbation", "warning"), card.tag)
    }

    @Test
    fun approved() {
        val mandate = MANDATE.copy(
            status = MandateStatus.APPROVED,
            mandateType = MandateType.LISTING,
            neighborhoodIds = listOf(BONAPRISO),
        )

        val card = card(
            mandate = mandate,
            listings = mapOf(201L to ListingSummary(id = 501, mandateId = 201, status = ListingStatus.PUBLISHED)),
            stats = mapOf(501L to ListingStats(501, 2, 0, 3)),
        )

        assertEquals("Propriétaire · Maison", card.title)
        assertEquals(MandateRules.Tag("Actif", "success"), card.tag)
        assertEquals(
            listOf("Secteur" to "Bonapriso", "Loyer" to "250K\u00a0FCFA / mois", "Disponibilité" to "Dans 1 à 3\u00a0mois"),
            card.facts,
        )
        assertEquals("2 personnes intéressées · 3 visites", card.stats)
    }

    /** A landlord's signed mandate (203), with [listing] as the listings' search returns it. */
    private fun listed(listing: ListingSummary?, stats: Map<Long, ListingStats>? = emptyMap()): MandateCardView =
        card(
            mandate = MANDATE.copy(id = 203, status = MandateStatus.APPROVED, mandateType = MandateType.LISTING),
            listings = listOfNotNull(listing).associateBy { it.mandateId },
            stats = stats,
        )

    @Test
    fun `listing published - activity`() {
        val published = ListingSummary(id = 501, mandateId = 203, status = ListingStatus.PUBLISHED)

        assertEquals("2 personnes intéressées · 3 visites", listed(published, mapOf(501L to ListingStats(501, 2, 1, 2))).stats)
        assertEquals(
            "Aucune personne intéressée pour l'instant · Aucune visite",
            listed(published, mapOf(501L to ListingStats(501, 0, 0, 0))).stats,
        )
        assertNull(listed(published, mapOf(502L to ListingStats(502, 2, 0, 0))).stats) // No row for 501
        assertNull(listed(published, null).stats) // Unavailable
        assertNull(listed(published.copy(status = ListingStatus.DRAFT), mapOf(501L to ListingStats(501, 2, 0, 0))).stats)
        assertNull(listed(null).stats)
    }

    @Test
    fun `listing to create`() {
        val card = listed(null)

        assertEquals(ListingLineView("Annonce", "/mandates/203", ListingRules.Tag("À créer", "neutral")), card.listing)
    }

    @Test
    fun `listing in draft`() {
        val card = listed(ListingSummary(id = 501, mandateId = 203, status = ListingStatus.DRAFT))

        assertEquals(ListingLineView("Annonce", "/listings/501", ListingRules.Tag("Brouillon", "info")), card.listing)
    }

    @Test
    fun `listing sent`() {
        val card = listed(ListingSummary(id = 501, mandateId = 203, status = ListingStatus.UNDER_VERIFICATION))

        assertEquals(ListingRules.Tag("En vérification", "warning"), card.listing?.tag)
    }

    @Test
    fun `listing published`() {
        val publishedAt = Date.from(Instant.parse("2026-10-16T10:00:00Z"))
        val card = listed(ListingSummary(id = 501, mandateId = 203, status = ListingStatus.PUBLISHED, publishedAt = publishedAt))

        assertEquals(ListingRules.Tag("Publiée le ${FORMATTER.date(publishedAt)}", "success"), card.listing?.tag)
    }

    @Test
    fun `listings unavailable - no line`() {
        val card = card(mandate = MANDATE.copy(id = 203, status = MandateStatus.APPROVED, mandateType = MandateType.LISTING), listings = null)

        assertNull(card.listing)
    }

    @Test
    fun `search - no listing line`() {
        val card = card(mandate = MANDATE.copy(status = MandateStatus.APPROVED))

        assertNull(card.listing)
    }

    @Test
    fun `whole city`() {
        val card = card(mandate = MANDATE.copy(neighborhoodIds = emptyList()))

        assertEquals("Secteur" to "Douala", card.facts[0])
    }

    @Test
    fun `whole city without its name`() {
        val card = MandateCards.of(MANDATE.copy(neighborhoodIds = emptyList()), emptyMap(), emptyMap(), FORMATTER)

        assertEquals("Secteur" to "Toute la ville", card.facts[0])
    }

    // Search stats

    @Test
    fun `search stats`() {
        val approved = MANDATE.copy(status = MandateStatus.APPROVED)
        val card = card(
            mandate = approved,
            mandateStats = mapOf(201L to MandateStats(201, properties = 3, interested = 1, upcomingVisits = 1, completedVisits = 2)),
        )

        assertEquals("3 biens · 1 intéressé · 3 visites", card.searchStats?.label)
        assertEquals("/mandates/201#matches-title", card.searchStats?.url)
    }

    @Test
    fun `search stats - unavailable`() {
        assertNull(card(MANDATE.copy(status = MandateStatus.APPROVED), mandateStats = null).searchStats)
    }

    @Test
    fun `search stats - no match`() {
        val card = card(
            mandate = MANDATE.copy(status = MandateStatus.APPROVED),
            mandateStats = mapOf(201L to MandateStats(201)),
        )

        assertEquals("Aucun bien correspondant pour l'instant", card.searchStats?.label)
    }

    @Test
    fun `not an approved search - no stats`() {
        assertNull(card(MANDATE, mandateStats = mapOf(201L to MandateStats(201, 3, 1, 0, 0))).searchStats)
    }
}
