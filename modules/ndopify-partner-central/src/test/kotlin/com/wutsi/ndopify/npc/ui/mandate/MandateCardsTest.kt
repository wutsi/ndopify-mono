package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractSummary
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
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

        val CONTRACT = Contract(
            id = "c-201",
            signatureRequestedAt = Date.from(Instant.parse("2026-10-15T10:00:00Z")),
            signatureDeadline = Date.from(Instant.parse("2026-10-22T10:00:00Z")),
            validUntil = Date.from(Instant.parse("2027-03-28T10:00:00Z")),
        )

        /** [CONTRACT], as the contract search returns it (the cards of the Espace agent). */
        val CONTRACT_SUMMARY = ContractSummary(
            id = CONTRACT.id,
            signatureDeadline = CONTRACT.signatureDeadline,
            validUntil = CONTRACT.validUntil,
        )

        /** [CONTRACT_SUMMARY], still to sign at the home tests' clock (AssignmentViewsTest.NOW, 15 January 2027). */
        val CONTRACT_2027 = CONTRACT_SUMMARY.copy(signatureDeadline = Date.from(Instant.parse("2027-01-19T10:00:00Z")))
    }

    @Test
    fun assigned() {
        val card = MandateCards.of(MANDATE, null, emptyMap(), NAMES, NOW, FORMATTER)

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
        assertEquals("Contactez le client, puis envoyez-lui le mandat à approuver.", card.nextStep)
    }

    @Test
    fun `pending approval`() {
        val card = MandateCards.of(MANDATE.copy(status = MandateStatus.PENDING_APPROVAL), CONTRACT_SUMMARY, emptyMap(), NAMES, NOW, FORMATTER)

        assertEquals(MandateRules.Tag("En approbation", "warning"), card.tag)
        assertEquals("Le client a jusqu'au 22 oct. 2026 pour signer le mandat.", card.nextStep)
    }

    @Test
    fun approved() {
        val mandate = MANDATE.copy(
            status = MandateStatus.APPROVED,
            mandateType = MandateType.LISTING,
            neighborhoodIds = listOf(BONAPRISO),
        )

        val card = MandateCards.of(mandate, CONTRACT_SUMMARY, mapOf(201L to ListingSummary(id = 501, mandateId = 201, status = ListingStatus.PUBLISHED)), NAMES, NOW, FORMATTER)

        assertEquals("Bailleur · Maison", card.title)
        assertEquals(MandateRules.Tag("Actif", "success"), card.tag)
        assertEquals(
            listOf("Secteur" to "Bonapriso", "Loyer" to "250K\u00a0FCFA / mois", "Disponibilité" to "Dans 1 à 3\u00a0mois"),
            card.facts,
        )
        assertEquals("Trouvez un locataire avant le 28 mars 2027.", card.nextStep)
    }

    /** A landlord's signed mandate (203), with [listing] as the listings' search returns it. */
    private fun listed(listing: ListingSummary?): MandateCardView =
        MandateCards.of(
            MANDATE.copy(id = 203, status = MandateStatus.APPROVED, mandateType = MandateType.LISTING),
            CONTRACT_SUMMARY,
            listOfNotNull(listing).associateBy { it.mandateId },
            NAMES,
            NOW,
            FORMATTER,
        )

    @Test
    fun `listing to create`() {
        val card = listed(null)

        assertEquals(ListingLineView("Annonce", "/mandates/203", ListingRules.Tag("À créer", "neutral")), card.listing)
        assertEquals("Créez l'annonce du bien, puis visitez-le pour la compléter.", card.nextStep)
    }

    @Test
    fun `listing in draft`() {
        val card = listed(ListingSummary(id = 501, mandateId = 203, status = ListingStatus.DRAFT))

        assertEquals(ListingLineView("Annonce n° 501", "/listings/501", ListingRules.Tag("Brouillon", "info")), card.listing)
        assertEquals("Complétez l'annonce, puis envoyez-la pour vérification.", card.nextStep)
    }

    @Test
    fun `listing sent`() {
        val card = listed(ListingSummary(id = 501, mandateId = 203, status = ListingStatus.UNDER_VERIFICATION))

        assertEquals(ListingRules.Tag("En vérification", "warning"), card.listing?.tag)
        assertEquals("Annonce en vérification : elle sera publiée sous peu.", card.nextStep)
    }

    @Test
    fun `listing published`() {
        val publishedAt = Date.from(Instant.parse("2026-10-16T10:00:00Z"))
        val card = listed(ListingSummary(id = 501, mandateId = 203, status = ListingStatus.PUBLISHED, publishedAt = publishedAt))

        assertEquals(ListingRules.Tag("Publiée le ${FORMATTER.date(publishedAt)}", "success"), card.listing?.tag)
        assertEquals("Trouvez un locataire avant le 28 mars 2027.", card.nextStep)
    }

    @Test
    fun `listings unavailable - no line, the mandate's next step`() {
        val card = MandateCards.of(
            MANDATE.copy(id = 203, status = MandateStatus.APPROVED, mandateType = MandateType.LISTING),
            CONTRACT_SUMMARY,
            null,
            NAMES,
            NOW,
            FORMATTER,
        )

        assertNull(card.listing)
        assertEquals("Trouvez un locataire avant le 28 mars 2027.", card.nextStep)
    }

    @Test
    fun `search - no listing line`() {
        val card = MandateCards.of(MANDATE.copy(status = MandateStatus.APPROVED), CONTRACT_SUMMARY, emptyMap(), NAMES, NOW, FORMATTER)

        assertNull(card.listing)
        assertEquals("Trouvez un bien pour le client avant le 28 mars 2027.", card.nextStep)
    }

    @Test
    fun `whole city`() {
        val card = MandateCards.of(MANDATE.copy(neighborhoodIds = emptyList()), null, emptyMap(), NAMES, NOW, FORMATTER)

        assertEquals("Secteur" to "Douala", card.facts[0])
    }

    @Test
    fun `whole city without its name`() {
        val card = MandateCards.of(MANDATE.copy(neighborhoodIds = emptyList()), null, emptyMap(), emptyMap(), NOW, FORMATTER)

        assertEquals("Secteur" to "Toute la ville", card.facts[0])
    }
}
