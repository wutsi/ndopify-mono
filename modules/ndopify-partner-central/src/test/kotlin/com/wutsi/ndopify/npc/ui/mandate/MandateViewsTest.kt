package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.CONTRACT
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NAMES
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NOW
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateViewsTest {
    companion object {
        val CUSTOMER = Customer(
            id = 555,
            party = Party(id = 600, firstName = "Ray", lastName = "Sponsible", email = "ray@gmail.com"),
            whatsappNumber = "+237699123456",
        )

        val ACCEPTED = AssignmentSummary(
            id = 7,
            mandateId = 201,
            agentId = 20,
            status = AssignmentStatus.ACCEPTED,
            modifiedAt = date("2026-10-10T10:00:00Z"),
        )

        // Sent on 15 October (the contract's signatureRequestedAt), not edited since.
        val PENDING = MANDATE.copy(
            status = MandateStatus.PENDING_APPROVAL,
            contractId = "c-201",
            preferredContactPeriod = ContactPeriodType.MORNING,
            modifiedAt = date("2026-10-15T10:00:00Z"),
        )

        val APPROVED = PENDING.copy(status = MandateStatus.APPROVED, approvedAt = date("2026-10-20T10:00:00Z"))

        // An approved land purchase requiring two documents.
        val LAND_SALE = APPROVED.copy(
            listingType = ListingType.SALE,
            propertyType = PropertyType.LAND,
            titleDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
            price = Money(15_000_000, "XAF", PeriodicityType.NONE),
        )

        fun date(iso: String): Date = Date.from(Instant.parse(iso))
    }

    private fun view(
        mandate: com.wutsi.ndopify.mandate.dto.Mandate,
        contract: com.wutsi.ndopify.contract.dto.Contract? = CONTRACT,
        customer: Customer? = CUSTOMER,
        accepted: AssignmentSummary? = ACCEPTED,
        now: Long = NOW,
    ) = MandateViews.of(mandate, contract, customer, accepted, NAMES, now, FORMATTER)

    @Test
    fun assigned() {
        val view = view(MANDATE, contract = null)

        assertEquals(201L, view.id)
        assertEquals("Locataire · Maison", view.title)
        assertEquals(MandateRules.Tag("Assigné", "info"), view.tag)
        assertEquals("10 oct. 2026", view.acceptedAt)
        assertEquals(0, view.currentStep)
        assertEquals(
            listOf("ASSIGNED", "PENDING_APPROVAL", "APPROVED", "CLOSED"),
            view.steps.map { it.status },
        )
        assertEquals(listOf("Assigné", "En approbation", "Actif", "Fermé"), view.steps.map { it.label })
        assertEquals(listOf("10 oct. 2026", null, null, null), view.steps.map { it.date })
        assertEquals(listOf(false, false, false, false), view.steps.map { it.done })
        assertEquals("Étape 1 sur 4 · Assigné, le 10 oct. 2026", view.stepSummary)
        assertTrue(view.editable)
        assertFalse(view.closable)
        assertTrue(view.cancellable)
        assertNull(view.requestedAt)
        assertNull(view.signatureTtlDays) // No contract yet
    }

    @Test
    fun `pending approval`() {
        val view = view(PENDING)

        assertEquals(1, view.currentStep)
        assertEquals(listOf("10 oct. 2026", "15 oct. 2026", null, null), view.steps.map { it.date })
        assertEquals(listOf(true, false, false, false), view.steps.map { it.done })
        assertEquals("15 oct. 2026", view.requestedAt)
        assertEquals("22 oct. 2026", view.signatureDeadline)
        assertEquals(7, view.signatureTtlDays) // From the contract's dates
        assertFalse(view.deadlinePassed)
        assertTrue(view.editable)
        assertFalse(view.closable)
        assertTrue(view.cancellable)
    }

    @Test
    fun `contract terms`() {
        val view = view(APPROVED, contract = CONTRACT.copy(durationMonths = 3, visitFee = Money(5000, "XAF")))

        assertTrue(view.hasContract)
        assertEquals("3 mois", view.contractDuration)
        assertEquals("5K FCFA", view.visitFee!!.replace('\u00a0', ' '))
    }

    @Test
    fun `contract terms - no fee`() {
        val view = view(APPROVED, contract = CONTRACT.copy(durationMonths = 6))

        assertTrue(view.hasContract)
        assertEquals("6 mois", view.contractDuration)
        assertNull(view.visitFee)
    }

    @Test
    fun `contract terms - no contract`() {
        val view = view(MANDATE, contract = null)

        assertFalse(view.hasContract)
        assertNull(view.contractDuration)
        assertNull(view.visitFee)
    }

    @Test
    fun `a sale contract shows its commission`() {
        val view = view(
            MANDATE.copy(listingType = ListingType.SALE),
            contract = CONTRACT.copy(durationMonths = 6, commissionRate = 7),
        )

        assertEquals("7 % du prix de vente", view.commission)
    }

    @Test
    fun `a rental has no commission row`() {
        val view = view(MANDATE.copy(listingType = ListingType.RENTAL), contract = CONTRACT.copy(commissionRate = null))

        assertNull(view.commission)
    }

    @Test
    fun `pending approval - deadline passed`() {
        val view = view(PENDING, now = date("2026-10-23T10:00:00Z").time)

        assertTrue(view.deadlinePassed)
        assertEquals("22 oct. 2026", view.signatureDeadline)
    }

    @Test
    fun approved() {
        val view = view(APPROVED)

        assertEquals(2, view.currentStep)
        assertEquals("20 oct. 2026", view.approvedAt)
        assertEquals("28 mars 2027", view.validUntil)
        assertEquals(listOf(true, true, false, false), view.steps.map { it.done })
        assertEquals("Étape 3 sur 4 · Actif, le 20 oct. 2026", view.stepSummary)
        assertFalse(view.editable)
        assertTrue(view.closable)
        assertTrue(view.cancellable)
    }

    @Test
    fun closed() {
        val view = view(APPROVED.copy(status = MandateStatus.CLOSED, closedAt = date("2026-11-01T10:00:00Z"), finalPrice = 240000, age = 12))

        assertEquals(3, view.currentStep)
        assertEquals(listOf(true, true, true, true), view.steps.map { it.done })
        assertEquals("Fermé", view.steps.last().label)
        assertEquals("01 nov. 2026", view.closedAt)
        assertEquals("240K\u00a0FCFA / mois", view.finalPrice)
        assertEquals("12 jours", view.age)
        assertFalse(view.editable)
        assertFalse(view.closable)
        assertFalse(view.cancellable)
    }

    @Test
    fun `a closed sale shows its final price without a month`() {
        val view = view(
            APPROVED.copy(status = MandateStatus.CLOSED, listingType = ListingType.SALE, finalPrice = 15_000_000, age = 12),
        )

        assertTrue(view.sale)
        assertEquals("15M\u00a0FCFA", view.finalPrice)
    }

    @Test
    fun `a closed rental is unchanged`() {
        val view = view(APPROVED.copy(status = MandateStatus.CLOSED, finalPrice = 240000))

        assertFalse(view.sale)
        assertEquals("240K\u00a0FCFA / mois", view.finalPrice)
    }

    @Test
    fun `a closed mandate without a final price leaves the line out`() {
        assertNull(view(APPROVED.copy(status = MandateStatus.CLOSED, listingType = ListingType.SALE, finalPrice = null)).finalPrice)
    }

    @Test
    fun `an unknown listing type reads as a rental`() {
        assertFalse(view(MANDATE.copy(listingType = ListingType.UNKNOWN)).sale)
    }

    @Test
    fun `closed - one day`() {
        assertEquals("1 jour", view(APPROVED.copy(status = MandateStatus.CLOSED, age = 1)).age)
        assertTrue(view(APPROVED.copy(status = MandateStatus.CLOSED)).steps.none { it.cancelled })
    }

    @Test
    fun cancelled() {
        // Cancelled before its approval: the steps it skipped aren't done.
        val view = view(
            PENDING.copy(
                status = MandateStatus.CANCELLED,
                closedAt = date("2026-10-19T10:00:00Z"),
                cancellationCode = MandateCancellationCode.CHANGE_OF_PLANS,
                cancellationReason = "Il reste chez lui",
            ),
        )

        assertEquals(3, view.currentStep)
        assertEquals("Annulé", view.steps.last().label)
        assertEquals(listOf(true, true, false, true), view.steps.map { it.done })
        assertEquals(listOf(false, false, false, true), view.steps.map { it.cancelled })
        assertEquals("Étape 4 sur 4 · Annulé, le 19 oct. 2026", view.stepSummary)
        assertEquals("changement de projet", view.cancellation)
        assertEquals("Il reste chez lui", view.cancellationReason)
        assertFalse(view.cancellable)
    }

    @Test
    fun contact() {
        val contact = view(PENDING).contact!!

        assertEquals("Ray Sponsible", contact.name)
        assertEquals("ray@gmail.com", contact.email)
        assertEquals("+237 6 99 12 34 56", contact.whatsappDisplay)
        assertEquals("237699123456", contact.whatsappDigits)
        assertEquals("+237699123456", contact.whatsapp)
        assertEquals("le matin", contact.period)
    }

    @Test
    fun `contact - foreign number shown as is`() {
        val contact = view(MANDATE, customer = CUSTOMER.copy(whatsappNumber = "+15145550000")).contact!!

        assertEquals("+15145550000", contact.whatsappDisplay)
        assertNull(contact.period)
    }

    @Test
    fun `contact unavailable`() {
        assertNull(view(MANDATE, customer = null).contact)
    }

    @Test
    fun `no accepted assignment`() {
        val view = view(MANDATE, accepted = null)

        assertNull(view.acceptedAt)
        assertEquals("Étape 1 sur 4 · Assigné", view.stepSummary)
    }

    @Test
    fun `contract unavailable`() {
        val view = view(APPROVED, contract = null)

        assertNull(view.requestedAt)
        assertNull(view.signatureDeadline)
        assertNull(view.validUntil)
        assertEquals("20 oct. 2026", view.approvedAt)
    }

    @Test
    fun wording() {
        val client = view(MANDATE)
        assertEquals("le client", client.words.the)
        assertFalse(client.owner)

        val owner = view(MANDATE.copy(mandateType = MandateType.LISTING))
        assertEquals("Le bailleur", owner.words.theUpper)
        assertTrue(owner.owner)
        assertEquals("Propriétaire · Maison", owner.title)
        assertEquals("Proposez des biens au client et organisez les visites.", client.nextStepHint)
        assertEquals("Présentez le bien aux locataires et organisez les visites.", owner.nextStepHint)
    }

    @Test
    fun `a sale search's page words the buyer`() {
        val view = view(MANDATE.copy(listingType = ListingType.SALE, price = Money(15_000_000, "XAF", PeriodicityType.NONE)))

        assertTrue(view.sale)
        assertEquals("Acheteur · Maison", view.title)
        assertEquals("Acheteur", view.words.role)
        assertEquals("Budget" to "15M\u00a0FCFA", view.keyCriteria[1])
        assertEquals("Achat prévu" to "Dans 1 à 3\u00a0mois", view.keyCriteria[2])
        assertEquals("Proposez des biens à l'acheteur et organisez les visites.", view.nextStepHint)
    }

    @Test
    fun `a sale listing's page words the seller`() {
        val view = view(
            MANDATE.copy(
                mandateType = MandateType.LISTING,
                listingType = ListingType.SALE,
                price = Money(45_000_000, "XAF", PeriodicityType.NONE),
            ),
        )

        assertTrue(view.sale)
        assertEquals("Vendeur · Maison", view.title)
        assertEquals("Vendeur", view.words.role)
        assertEquals("Prix" to "45M\u00a0FCFA", view.keyCriteria[1])
        assertEquals("Disponibilité" to "Dans 1 à 3\u00a0mois", view.keyCriteria[2])
        assertEquals("Présentez le bien aux acheteurs et organisez les visites.", view.nextStepHint)
    }

    @Test
    fun criteria() {
        val view = view(MANDATE.copy(waterWell = true))

        assertEquals(
            listOf(
                "Secteurs" to "Bonapriso, Akwa — Douala",
                "Budget" to "250K\u00a0FCFA / mois",
                "Emménagement" to "Dans 1 à 3\u00a0mois",
                "Type de bien" to "Maison, 3 chambres",
                "Autres précisions" to "Près d'une école",
            ),
            view.keyCriteria,
        )
        assertEquals(
            listOf("Catégorie" to "Résidentiel", "Meublé" to "Indifférent", "Parking" to "Indifférent", "Accès à l'eau" to "Forage"),
            view.moreCriteria,
        )
    }

    @Test
    fun `criteria - one room, no comments`() {
        val view = view(MANDATE.copy(rooms = "1", comments = null))

        assertEquals("Type de bien" to "Maison, 1 chambre", view.keyCriteria.last())
    }

    @Test
    fun `a land purchase keeps the documents visible and shows the note`() {
        val view = view(LAND_SALE)

        assertTrue(view.titleDocumentsRequired)
        assertTrue(view.keyCriteria.contains("Documents exigés" to "Titre foncier · Plan de bornage"))
        assertFalse(view.moreCriteria.any { it.first == "Documents exigés" })
    }

    @Test
    fun `a land purchase without documents shows the row, not the note`() {
        val view = view(LAND_SALE.copy(titleDocuments = emptyList()))

        assertFalse(view.titleDocumentsRequired)
        assertTrue(view.keyCriteria.contains("Documents exigés" to "Aucun document exigé"))
    }

    @Test
    fun `a rental has no documents row nor note`() {
        val view = view(MANDATE)

        assertFalse(view.titleDocumentsRequired)
        assertFalse((view.keyCriteria + view.moreCriteria).any { it.first == "Documents exigés" })
    }

    @Test
    fun `a non-land sale shows its documents and the note`() {
        val view = view(
            APPROVED.copy(
                listingType = ListingType.SALE,
                propertyType = PropertyType.HOUSE,
                titleDocuments = listOf(TitleDocumentType.LAND_TITLE),
            ),
        )

        assertTrue(view.titleDocumentsRequired)
        assertTrue(view.keyCriteria.contains("Documents exigés" to "Titre foncier"))
    }

    @Test
    fun `a seller's land mandate has no documents exigés row nor note`() {
        val view = view(
            MANDATE.copy(
                mandateType = MandateType.LISTING,
                listingType = ListingType.SALE,
                propertyType = PropertyType.LAND,
                ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE),
            ),
        )

        assertFalse(view.titleDocumentsRequired)
        assertFalse((view.keyCriteria + view.moreCriteria).any { it.first == "Documents exigés" })
    }
}
