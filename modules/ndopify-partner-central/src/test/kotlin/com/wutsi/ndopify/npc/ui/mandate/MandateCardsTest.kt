package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractSummary
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
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
        val card = MandateCards.of(MANDATE, null, NAMES, NOW, FORMATTER)

        assertEquals(201L, card.id)
        assertEquals("Locataire · Maison", card.title)
        assertEquals(MandateRules.Tag("Assigné", "info"), card.tag)
        assertEquals(
            listOf(
                "Secteurs" to "Bonapriso, Akwa",
                "Budget" to "250\u00a0000\u00a0FCFA / mois",
                "Emménagement" to "Dans 1 à 3 mois",
            ),
            card.facts,
        )
        assertEquals("Contactez le client, puis envoyez-lui le mandat à approuver.", card.nextStep)
    }

    @Test
    fun `pending approval`() {
        val card = MandateCards.of(MANDATE.copy(status = MandateStatus.PENDING_APPROVAL), CONTRACT_SUMMARY, NAMES, NOW, FORMATTER)

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

        val card = MandateCards.of(mandate, CONTRACT_SUMMARY, NAMES, NOW, FORMATTER)

        assertEquals("Bailleur · Maison", card.title)
        assertEquals(MandateRules.Tag("Actif", "success"), card.tag)
        assertEquals(
            listOf("Secteur" to "Bonapriso", "Loyer" to "250\u00a0000\u00a0FCFA / mois", "Disponibilité" to "Dans 1 à 3 mois"),
            card.facts,
        )
        assertEquals("Trouvez un locataire avant le 28 mars 2027.", card.nextStep)
    }

    @Test
    fun `whole city`() {
        val card = MandateCards.of(MANDATE.copy(neighborhoodIds = emptyList()), null, NAMES, NOW, FORMATTER)

        assertEquals("Secteur" to "Toute la ville", card.facts[0])
    }
}
