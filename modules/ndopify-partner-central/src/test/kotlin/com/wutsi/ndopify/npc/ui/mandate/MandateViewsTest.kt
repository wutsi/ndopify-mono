package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.CONTRACT
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NAMES
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NOW
import com.wutsi.ndopify.party.dto.Party
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

        // Sent on 15 October (the deadline, 22 October, less 7 days), not edited since.
        val PENDING = MANDATE.copy(
            status = MandateStatus.PENDING_APPROVAL,
            contractId = "c-201",
            preferredContactPeriod = ContactPeriodType.MORNING,
            modifiedAt = date("2026-10-15T10:00:00Z"),
        )

        val APPROVED = PENDING.copy(status = MandateStatus.APPROVED, approvedAt = date("2026-10-20T10:00:00Z"))

        fun date(iso: String): Date = Date.from(Instant.parse(iso))
    }

    private fun view(
        mandate: com.wutsi.ndopify.mandate.dto.Mandate,
        contract: com.wutsi.ndopify.contract.dto.Contract? = CONTRACT,
        customer: Customer? = CUSTOMER,
        accepted: AssignmentSummary? = ACCEPTED,
        now: Long = NOW,
    ) = MandateViews.of(mandate, contract, customer, accepted, NAMES, now, FORMATTER, 7)

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
        assertEquals(7, view.signatureTtlDays)
    }

    @Test
    fun `pending approval`() {
        val view = view(PENDING)

        assertEquals(1, view.currentStep)
        assertEquals(listOf("10 oct. 2026", "15 oct. 2026", null, null), view.steps.map { it.date })
        assertEquals(listOf(true, false, false, false), view.steps.map { it.done })
        assertEquals("15 oct. 2026", view.requestedAt)
        assertEquals("22 oct. 2026", view.signatureDeadline)
        assertFalse(view.deadlinePassed)
        assertFalse(view.editedSince)
        assertTrue(view.editable)
        assertFalse(view.closable)
        assertTrue(view.cancellable)
    }

    @Test
    fun `pending approval - deadline passed`() {
        val view = view(PENDING, now = date("2026-10-23T10:00:00Z").time)

        assertTrue(view.deadlinePassed)
        assertEquals("22 oct. 2026", view.signatureDeadline)
    }

    @Test
    fun `pending approval - edited since`() {
        val view = view(PENDING.copy(modifiedAt = date("2026-10-16T10:00:00Z")))

        assertTrue(view.editedSince)
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
        assertEquals("240\u00a0000\u00a0FCFA / mois", view.finalPrice)
        assertEquals("12 jours", view.age)
        assertFalse(view.editable)
        assertFalse(view.closable)
        assertFalse(view.cancellable)
    }

    @Test
    fun `closed - one day`() {
        assertEquals("1 jour", view(APPROVED.copy(status = MandateStatus.CLOSED, age = 1)).age)
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
        assertEquals("+237699123456", contact.whatsapp)
        assertEquals("+237 6 99 12 34 56", contact.whatsappDisplay)
        assertEquals("237699123456", contact.whatsappDigits)
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
        assertEquals("Bailleur · Maison", owner.title)
    }

    @Test
    fun criteria() {
        val view = view(MANDATE.copy(waterWell = true))

        assertEquals(
            listOf(
                "Secteurs" to "Bonapriso, Akwa — Douala",
                "Budget" to "250\u00a0000\u00a0FCFA / mois",
                "Emménagement" to "Dans 1 à 3 mois",
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
}
