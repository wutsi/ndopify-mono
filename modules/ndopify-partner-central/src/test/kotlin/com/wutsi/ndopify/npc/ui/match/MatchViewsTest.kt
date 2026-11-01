package com.wutsi.ndopify.npc.ui.match

import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.npc.ui.mandate.MandateRules
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.LISTINGS
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.LISTING_A
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.LOCATIONS
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.MANDATE
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.MATCH_A
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.MATCH_B
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.MATCH_C
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.TODAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchViewsTest {
    @Test
    fun section() {
        val section = MatchViews.section(MANDATE, listOf(MATCH_B, MATCH_A, MATCH_C), LISTINGS, LOCATIONS, TODAY, FORMATTER)

        assertFalse(section.unavailable)
        assertEquals(0, section.dismissed)
        assertEquals(listOf(9001L, 9002L, 9003L), section.matches.map { it.id })

        val view = section.matches[0]
        assertEquals(3012L, view.listingId)
        assertEquals("Appartement · 3 chambres", view.title)
        assertEquals(MandateRules.Tag("Nouveau", "info"), view.tag)
        assertEquals("/listings/3012", view.listingUrl)
        assertEquals(
            listOf(
                "Correspondance" to "95 %",
                "Quartier" to "Bonapriso",
                "Loyer" to "240K\u00a0FCFA / mois",
                "Disponibilité" to "Dès le 01 déc. 2026",
            ),
            view.facts,
        )
        assertNull(view.answer)
        assertEquals(6, view.fit.size)
        assertTrue(view.actionable)
        assertNull(section.proposals)
    }

    @Test
    fun `refused - left out, counted in the proposals`() {
        val matches = listOf(
            MATCH_A,
            MATCH_B.copy(status = MatchStatus.REJECTED),
            MATCH_C.copy(status = MatchStatus.PROPOSED),
        )

        val section = MatchViews.section(MANDATE, matches, LISTINGS, LOCATIONS, TODAY, FORMATTER)

        assertEquals(listOf(9001L, 9002L), section.matches.map { it.id })
        assertEquals("1 refusé · 1 en attente", section.proposals)
    }

    @Test
    fun `rent - not round`() {
        val listings = mapOf(3012L to LISTING_A.copy(price = 152_500))

        val view = MatchViews.section(MANDATE, listOf(MATCH_A), listings, LOCATIONS, TODAY, FORMATTER).matches[0]

        assertEquals("Loyer" to "152,5K\u00a0FCFA / mois", view.facts[2])
    }

    @Test
    fun `available now`() {
        val listings = mapOf(3012L to LISTING_A.copy(availableFrom = TODAY.minusDays(3)))

        val view = MatchViews.section(MANDATE, listOf(MATCH_A), listings, LOCATIONS, TODAY, FORMATTER).matches[0]

        assertEquals("Disponibilité" to "Libre maintenant", view.facts[3])
    }

    @Test
    fun `available today`() {
        val listings = mapOf(3012L to LISTING_A.copy(availableFrom = TODAY))

        val view = MatchViews.section(MANDATE, listOf(MATCH_A), listings, LOCATIONS, TODAY, FORMATTER).matches[0]

        assertEquals("Disponibilité" to "Libre maintenant", view.facts[3])
    }

    @Test
    fun `no availability date`() {
        val listings = mapOf(3012L to LISTING_A.copy(availableFrom = null))

        val view = MatchViews.section(MANDATE, listOf(MATCH_A), listings, LOCATIONS, TODAY, FORMATTER).matches[0]

        assertEquals("Disponibilité" to "Libre maintenant", view.facts[3])
    }

    @Test
    fun `without neighbourhood nor rooms`() {
        val listings = mapOf(3012L to LISTING_A.copy(neighborhoodId = null, rooms = null))

        val view = MatchViews.section(MANDATE, listOf(MATCH_A), listings, LOCATIONS, TODAY, FORMATTER).matches[0]

        assertEquals("Appartement", view.title)
        assertEquals("Quartier" to "Douala", view.facts[1])
    }

    @Test
    fun `one room`() {
        val listings = mapOf(3012L to LISTING_A.copy(rooms = 1))

        val view = MatchViews.section(MANDATE, listOf(MATCH_A), listings, LOCATIONS, TODAY, FORMATTER).matches[0]

        assertEquals("Appartement · 1 chambre", view.title)
    }

    @Test
    fun `match without its listing`() {
        val listings = LISTINGS - 3027L

        val section = MatchViews.section(MANDATE, listOf(MATCH_A, MATCH_C, MATCH_B), listings, LOCATIONS, TODAY, FORMATTER)

        assertEquals(listOf(9001L, 9003L), section.matches.map { it.id })
    }

    @Test
    fun dismissed() {
        val matches = listOf(MATCH_A, MATCH_B.copy(status = MatchStatus.DISMISSED), MATCH_C.copy(status = MatchStatus.DISMISSED))

        val section = MatchViews.section(MANDATE, matches, LISTINGS, LOCATIONS, TODAY, FORMATTER)

        assertEquals(listOf(9001L), section.matches.map { it.id })
        assertEquals(2, section.dismissed)
    }

    @Test
    fun `not actionable once proposed`() {
        val matches = listOf(MATCH_A.copy(status = MatchStatus.PROPOSED), MATCH_B.copy(status = MatchStatus.ACCEPTED))

        val section = MatchViews.section(MANDATE, matches, LISTINGS, LOCATIONS, TODAY, FORMATTER)

        assertEquals(listOf(9003L, 9001L), section.matches.map { it.id })
        assertFalse(section.matches.any { it.actionable })
        assertEquals("Envoyé", section.matches[1].answer)
    }

    @Test
    fun `matches unavailable`() {
        val section = MatchViews.section(MANDATE, null, LISTINGS, LOCATIONS, TODAY, FORMATTER)

        assertTrue(section.unavailable)
        assertTrue(section.matches.isEmpty())
    }

    @Test
    fun `listings unavailable`() {
        val section = MatchViews.section(MANDATE, listOf(MATCH_A), null, LOCATIONS, TODAY, FORMATTER)

        assertTrue(section.unavailable)
        assertTrue(section.matches.isEmpty())
    }

    @Test
    fun empty() {
        val section = MatchViews.section(MANDATE, emptyList(), emptyMap(), LOCATIONS, TODAY, FORMATTER)

        assertFalse(section.unavailable)
        assertTrue(section.matches.isEmpty())
    }
}
