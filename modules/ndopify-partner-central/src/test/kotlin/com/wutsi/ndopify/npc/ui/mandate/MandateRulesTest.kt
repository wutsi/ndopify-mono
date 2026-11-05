package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MandateRulesTest {

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

    private fun date(iso: String): Date = Date.from(Instant.parse(iso))
}