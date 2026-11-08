package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.visit.dto.VisitStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingVisitTest {
    @Test
    fun `no requested visit - no callout`() {
        assertNull(PendingVisit.of(emptyList()))
    }

    @Test
    fun `one requested visit names the client`() {
        val visit = card(7001, expiresIn = "42 h")
        val view = PendingVisit.of(listOf(PendingVisitCandidate(visit, "Sandrine Eboa", "proposal-9002")))!!

        assertEquals("1 visite à confirmer · 42 h — Sandrine Eboa", view.label)
        assertEquals("#proposal-9002", view.url)
    }

    @Test
    fun `several requested visits use the soonest expiry and no name`() {
        val later = card(7002, expiresIn = "44 h", expiresAt = 2_000L)
        val sooner = card(7001, expiresIn = "8 h", expiresAt = 1_000L)

        val view = PendingVisit.of(
            listOf(
                PendingVisitCandidate(later, "Later", "match-9002"),
                PendingVisitCandidate(sooner, "Sooner", "match-9001"),
            ),
        )!!

        assertEquals("2 visites à confirmer · la plus urgente dans 8 h", view.label)
        assertEquals("#match-9001", view.url)
    }

    @Test
    fun `a confirmed visit is not pending`() {
        val view = PendingVisit.of(
            listOf(PendingVisitCandidate(card(7001, expiresIn = "8 h").copy(status = VisitStatus.CONFIRMED), "Sandrine", "match-9001")),
        )

        assertNull(view)
    }

    private fun card(id: Long, expiresIn: String?, expiresAt: Long? = 1_000L) = VisitCardView(
        id = id,
        matchId = 9001,
        customerName = "Sandrine Eboa",
        type = "Sur place",
        video = false,
        status = VisitStatus.REQUESTED,
        kindLabel = "Visite demandée",
        whenText = "pour le sam. 7 novembre 2026 à 10:30",
        tag = null,
        whenLabel = "sam. 7 novembre 2026 à 10:30",
        at = 1_000L,
        listing = "Sur place · Annonce n° 3012",
        expiresAt = expiresAt,
        expiresIn = expiresIn,
        urgent = false,
    )
}
