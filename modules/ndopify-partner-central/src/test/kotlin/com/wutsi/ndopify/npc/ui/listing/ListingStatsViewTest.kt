package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.ListingStats
import com.wutsi.ndopify.npc.ui.match.MatchRules
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListingStatsViewTest {
    @Test
    fun label() {
        assertEquals("2 personnes intéressées · 3 visites", ListingStatsView.of(ListingStats(501, 2, 1, 2))?.label)
        assertEquals("1 personne intéressée · 1 visite", ListingStatsView.of(ListingStats(501, 1, 0, 1))?.label)
        assertEquals(
            "Aucune personne intéressée pour l'instant · Aucune visite",
            ListingStatsView.of(ListingStats(501, 0, 0, 0))?.label,
        )
        assertEquals("2 personnes intéressées", ListingStatsView(MatchRules.interest(2), null).label)
        assertNull(ListingStatsView.of(null))
    }
}
