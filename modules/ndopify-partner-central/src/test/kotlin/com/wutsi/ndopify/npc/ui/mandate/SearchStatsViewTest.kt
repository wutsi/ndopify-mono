package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStats
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchStatsViewTest {
    private val mandate = Mandate(
        id = 201,
        mandateType = MandateType.SEARCH,
        status = MandateStatus.APPROVED,
    )

    @Test
    fun `label - pool, interest and visits`() {
        val stats = SearchStatsView.of(mandate, MandateStats(201, 12, 1, 1, 2))!!

        assertEquals(12, stats.properties)
        assertEquals(1, stats.interested)
        assertEquals("12 biens · 1 intéressé · 3 visites", stats.label)
        assertEquals("/mandates/201#matches-title", stats.url)
    }

    @Test
    fun `label drops zero segments`() {
        assertEquals("12 biens", SearchStatsView.of(mandate, MandateStats(201, 12, 0, 0, 0))!!.label)
        assertEquals(
            "12 biens · 3 visites",
            SearchStatsView.of(mandate, MandateStats(201, 12, 0, 1, 2))!!.label,
        )
        assertEquals(
            "12 biens · 1 intéressé",
            SearchStatsView.of(mandate, MandateStats(201, 12, 1, 0, 0))!!.label,
        )
    }

    @Test
    fun `label - empty`() {
        val stats = SearchStatsView.of(mandate, MandateStats(201))!!
        assertEquals("Aucun bien correspondant pour l'instant", stats.label)
        assertFalse(stats.shown)
    }

    @Test
    fun `label - no pool but visits`() {
        val stats = SearchStatsView.of(mandate, MandateStats(201, properties = 0, interested = 0, upcomingVisits = 1, completedVisits = 1))!!
        assertEquals("2 visites", stats.label)
        assertTrue(stats.shown)
    }

    @Test
    fun `stats unavailable`() {
        assertNull(SearchStatsView.of(mandate, null))
    }

    @Test
    fun `label - singular`() {
        assertEquals(
            "1 bien · 1 intéressé · 1 visite",
            SearchStatsView.of(mandate, MandateStats(201, 1, 1, 0, 1))!!.label,
        )
    }

    @Test
    fun `mandate page - visits unavailable`() {
        val stats = SearchStatsView.of(mandate, emptyList(), null)

        assertEquals("Aucun bien correspondant pour l'instant", stats.label)
        assertNull(stats.visits)
    }
}
