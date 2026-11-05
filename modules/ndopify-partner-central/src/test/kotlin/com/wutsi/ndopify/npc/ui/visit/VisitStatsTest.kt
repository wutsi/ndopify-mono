package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.NOW
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.checkedIn
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.completed
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.confirmed
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.date
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.requested
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.summary
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisitStatsTest {
    private fun of(vararg visits: Visit) = VisitStats.of(visits.map { summary(it) }, NOW)

    @Test
    fun `no visits`() {
        val stats = of()

        assertEquals(0, stats?.upcoming)
        assertEquals(0, stats?.completed)
        assertEquals(0, stats?.total)
        assertEquals("Aucune visite", stats?.label)
    }

    @Test
    fun `upcoming`() {
        assertEquals("1 visite à venir", of(requested())?.label)
        assertEquals(2, of(confirmed(), checkedIn())?.upcoming)
        assertEquals("2 visites à venir", of(confirmed(), checkedIn())?.label)
    }

    @Test
    fun `completed visits`() {
        assertEquals("1 visite terminée", of(completed())?.label)
        assertEquals("2 visites terminées", of(completed(), completed().copy(id = 7002))?.label)
    }

    @Test
    fun `both`() {
        val stats = of(requested(), completed())

        assertEquals(2, stats?.total)
        assertEquals(1, stats?.upcoming)
        assertEquals(1, stats?.completed)
        assertEquals("2 visites · 1 à venir · 1 terminée", stats?.label)
    }

    @Test
    fun `stale requested`() {
        assertEquals("Aucune visite", of(requested().copy(createdAt = date("2026-11-03T09:00:00Z")))?.label)
    }

    @Test
    fun `cancelled, expired and no-show are not counted`() {
        val stats = of(
            requested().copy(status = VisitStatus.CANCELLED),
            requested().copy(status = VisitStatus.EXPIRED),
            requested().copy(status = VisitStatus.NO_SHOW),
        )

        assertEquals(0, stats?.total)
        assertEquals("Aucune visite", stats?.label)
    }

    @Test
    fun `unavailable`() {
        assertNull(VisitStats.of(null, NOW))
    }
}
