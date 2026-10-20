package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractSummary
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateRulesTest {
    private val now = date("2026-10-18T10:00:00Z").time

    @Test
    fun tag() {
        assertEquals(MandateRules.Tag("Assigné", "info"), MandateRules.tag(MandateStatus.ASSIGNED))
        assertEquals(MandateRules.Tag("En approbation", "warning"), MandateRules.tag(MandateStatus.PENDING_APPROVAL))
        assertEquals(MandateRules.Tag("Actif", "success"), MandateRules.tag(MandateStatus.APPROVED))
        assertEquals(MandateRules.Tag("Fermé", "neutral"), MandateRules.tag(MandateStatus.CLOSED))
        assertEquals(MandateRules.Tag("Annulé", "neutral"), MandateRules.tag(MandateStatus.CANCELLED))
    }

    @Test
    fun statuses() {
        assertEquals(
            setOf(MandateStatus.ASSIGNED, MandateStatus.PENDING_APPROVAL, MandateStatus.APPROVED),
            MandateRules.ACTIVE,
        )
        assertEquals(setOf(MandateStatus.ASSIGNED, MandateStatus.PENDING_APPROVAL), MandateRules.EDITABLE)
        assertEquals(MandateRules.ACTIVE + MandateStatus.CLOSED + MandateStatus.CANCELLED, MandateRules.SHOWN)
    }

    @Test
    fun words() {
        val owner = MandateRules.words(MandateType.LISTING)
        assertEquals("le bailleur", owner.the)
        assertEquals("Le bailleur", owner.theUpper)
        assertEquals("du bailleur", owner.of)
        assertEquals("son bien", owner.search)
        assertEquals("Son bien", owner.searchUpper)
        assertEquals("au bailleur", owner.to)

        val client = MandateRules.words(MandateType.SEARCH)
        assertEquals("le client", client.the)
        assertEquals("Le client", client.theUpper)
        assertEquals("du client", client.of)
        assertEquals("sa recherche", client.search)
        assertEquals("Sa recherche", client.searchUpper)
        assertEquals("au client", client.to)
    }

    @Test
    fun cancellation() {
        assertEquals("trouvé sans votre aide", MandateRules.cancellation(MandateCancellationCode.FOUND_INDEPENDENTLY))
        assertEquals("critères irréalistes", MandateRules.cancellation(MandateCancellationCode.UNREALISTIC_CRITERIA))
        assertEquals("autre raison", MandateRules.cancellation(MandateCancellationCode.UNKNOWN))
        assertEquals("autre raison", MandateRules.cancellation(null))
    }

    @Test
    fun requestedAt() {
        // The deadline isn't 7 days later: the date comes from the contract, never from the TTL
        val contract = Contract(
            signatureRequestedAt = date("2026-10-15T10:00:00Z"),
            signatureDeadline = date("2026-10-30T10:00:00Z"),
        )

        assertEquals(date("2026-10-15T10:00:00Z"), MandateRules.requestedAt(contract))
        assertNull(MandateRules.requestedAt(Contract(signatureDeadline = date("2026-10-22T10:00:00Z"))))
        assertNull(MandateRules.requestedAt(null))
    }

    @Test
    fun signatureTtlDays() {
        val contract = Contract(
            signatureRequestedAt = date("2026-10-15T10:00:00Z"),
            signatureDeadline = date("2026-10-22T10:00:00Z"),
        )

        assertEquals(7, MandateRules.signatureTtlDays(contract))
        assertEquals(3, MandateRules.signatureTtlDays(contract.copy(signatureDeadline = date("2026-10-18T10:00:00Z"))))
        assertNull(MandateRules.signatureTtlDays(contract.copy(signatureRequestedAt = null)))
        assertNull(MandateRules.signatureTtlDays(contract.copy(signatureDeadline = null)))
        assertNull(MandateRules.signatureTtlDays(null))
    }

    @Test
    fun editedSince() {
        val requestedAt = date("2026-10-15T10:00:00Z")

        assertTrue(MandateRules.editedSince(Mandate(modifiedAt = date("2026-10-16T10:00:00Z")), requestedAt))
        assertFalse(MandateRules.editedSince(Mandate(modifiedAt = requestedAt), requestedAt))
        assertFalse(MandateRules.editedSince(Mandate(modifiedAt = date("2026-10-14T10:00:00Z")), requestedAt))
        assertFalse(MandateRules.editedSince(Mandate(), null))
    }

    @Test
    fun `next step - assigned`() {
        assertEquals(
            "Contactez le client, puis envoyez-lui le mandat à approuver.",
            MandateRules.nextStep(Mandate(status = MandateStatus.ASSIGNED), null, now, FORMATTER),
        )
        assertEquals(
            "Contactez le bailleur, puis envoyez-lui le mandat à approuver.",
            MandateRules.nextStep(
                Mandate(status = MandateStatus.ASSIGNED, mandateType = MandateType.LISTING),
                null,
                now,
                FORMATTER,
            ),
        )
    }

    @Test
    fun `next step - pending approval`() {
        val contract = ContractSummary(signatureDeadline = date("2026-10-22T10:00:00Z"))

        assertEquals(
            "Le client a jusqu'au 22 oct. 2026 pour signer le mandat.",
            MandateRules.nextStep(Mandate(status = MandateStatus.PENDING_APPROVAL), contract, now, FORMATTER),
        )
    }

    @Test
    fun `next step - deadline passed`() {
        val contract = ContractSummary(signatureDeadline = date("2026-10-17T10:00:00Z"))

        assertEquals(
            "Délai de signature dépassé : renvoyez le mandat.",
            MandateRules.nextStep(Mandate(status = MandateStatus.PENDING_APPROVAL), contract, now, FORMATTER),
        )
    }

    @Test
    fun `next step - pending approval without contract`() {
        assertEquals(
            "Le client doit signer le mandat.",
            MandateRules.nextStep(Mandate(status = MandateStatus.PENDING_APPROVAL), null, now, FORMATTER),
        )
    }

    @Test
    fun `next step - approved`() {
        val contract = ContractSummary(validUntil = date("2027-03-28T10:00:00Z"))

        assertEquals(
            "Trouvez un bien pour le client avant le 28 mars 2027.",
            MandateRules.nextStep(Mandate(status = MandateStatus.APPROVED), contract, now, FORMATTER),
        )
        assertEquals(
            "Trouvez un bien pour le client.",
            MandateRules.nextStep(Mandate(status = MandateStatus.APPROVED), null, now, FORMATTER),
        )
    }

    @Test
    fun `next step - approved listing`() {
        val contract = ContractSummary(validUntil = date("2027-03-28T10:00:00Z"))
        val listing = Mandate(status = MandateStatus.APPROVED, mandateType = MandateType.LISTING)

        assertEquals(
            "Trouvez un locataire avant le 28 mars 2027.",
            MandateRules.nextStep(listing.copy(listingType = ListingType.RENTAL), contract, now, FORMATTER),
        )
        assertEquals(
            "Trouvez un acheteur avant le 28 mars 2027.",
            MandateRules.nextStep(listing.copy(listingType = ListingType.SALE), contract, now, FORMATTER),
        )
    }

    @Test
    fun `next step - closed`() {
        assertNull(MandateRules.nextStep(Mandate(status = MandateStatus.CLOSED), null, now, FORMATTER))
    }

    private fun date(iso: String): Date = Date.from(Instant.parse(iso))
}
