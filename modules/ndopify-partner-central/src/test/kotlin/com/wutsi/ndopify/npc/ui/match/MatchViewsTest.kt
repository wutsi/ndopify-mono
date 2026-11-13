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
import com.wutsi.ndopify.npc.ui.visit.VisitCardView
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures
import com.wutsi.ndopify.visit.dto.VisitStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchViewsTest {
    private fun section(
        matches: List<com.wutsi.ndopify.match.dto.Match> = listOf(MATCH_B, MATCH_A, MATCH_C),
        visits: Map<Long, VisitCardView> = emptyMap(),
    ) = MatchViews.section(MANDATE, matches, LISTINGS, visits, LOCATIONS, TODAY, FORMATTER)

    private fun visitCard(
        matchId: Long = 9001,
        status: VisitStatus = VisitStatus.REQUESTED,
        expiresAt: Long? = 1_000L,
        at: Long? = 1_000L,
    ) = VisitCardView(
        id = 7001,
        matchId = matchId,
        customerName = "Sandrine Eboa",
        type = "Sur place",
        video = false,
        status = status,
        kindLabel = "Visite demandée",
        whenText = "pour le sam. 7 novembre 2026 à 10:30",
        tag = null,
        whenLabel = "sam. 7 novembre 2026 à 10:30",
        at = at,
        listing = "Sur place · Appartement 3 chambres · Bonapriso",
        expiresAt = expiresAt,
        expiresIn = expiresAt?.let { "42 h" },
        urgent = false,
    )

    @Test
    fun `every card is grouped`() {
        val section = section()

        assertFalse(section.unavailable)
        assertFalse(section.visitsUnavailable)
        assertEquals(0, section.dismissed)
        assertEquals(listOf(9001L, 9002L, 9003L), section.matches.map { it.id })
        assertEquals(listOf("Nouveaux"), section.groups.map { it.label })
        assertFalse(section.hasPendingVisit)
        assertNull(section.outcomes)

        val view = section.matches[0]
        assertEquals(3012L, view.listingId)
        assertEquals("Appartement · 3 chambres", view.title)
        assertEquals(MandateRules.Tag("Nouveau", "info"), view.tag)
        assertEquals("/listings/3012", view.listingUrl)
        assertEquals(
            listOf(
                "Correspondance" to "95\u00a0%",
                "Quartier" to "Bonapriso",
                "Loyer" to "240K\u00a0FCFA / mois",
                "Disponibilité" to "Dès le 01 déc. 2026",
            ),
            view.facts,
        )
        assertNull(view.answer)
        assertNull(view.visit)
        assertNull(view.sent)
        assertEquals(MatchGroup.NEW, view.group)
        assertEquals(7, view.fit.size)
        assertTrue(view.actionable)
    }

    @Test
    fun `refused - left out, counted in the outcomes`() {
        val matches = listOf(
            MATCH_A,
            MATCH_B.copy(status = MatchStatus.REJECTED),
            MATCH_C.copy(status = MatchStatus.PROPOSED),
        )

        val section = section(matches)

        assertEquals(listOf(9001L, 9002L), section.matches.map { it.id })
        assertEquals("1 refusé", section.outcomes)
    }

    @Test
    fun `requested visit leads the groups`() {
        val visit = visitCard(expiresAt = 1_000L)
        val section = section(
            matches = listOf(
                MATCH_A.copy(status = MatchStatus.ACCEPTED, proposedAt = VisitFixtures.date("2026-11-01T10:00:00Z")),
                MATCH_B.copy(status = MatchStatus.ACTIVE),
            ),
            visits = mapOf(9001L to visit),
        )

        val first = section.matches.first()
        assertEquals(9001L, first.id)
        assertEquals(MatchGroup.REQUESTED, first.group)
        assertEquals(visit, first.visit)
        assertEquals("Envoyé le 01 nov. 2026", first.sent)
        assertEquals(listOf("À confirmer", "Nouveaux"), section.groups.map { it.label })
        assertTrue(section.hasPendingVisit)
    }

    @Test
    fun `upcoming visits come after the requests, soonest first`() {
        val sooner = visitCard(matchId = 9001, status = VisitStatus.CONFIRMED, expiresAt = null, at = 1_000L)
        val later = visitCard(matchId = 9003, status = VisitStatus.CHECKED_IN, expiresAt = null, at = 2_000L)
        val section = section(
            matches = listOf(
                MATCH_B.copy(status = MatchStatus.ACCEPTED),
                MATCH_A.copy(status = MatchStatus.ACCEPTED),
            ),
            visits = mapOf(9003L to later, 9001L to sooner),
        )

        assertEquals(listOf(9001L, 9003L), section.matches.map { it.id })
        assertEquals(listOf("Visites à venir"), section.groups.map { it.label })
        assertFalse(section.hasPendingVisit)
    }

    @Test
    fun `outcomes counts refused and unanswered`() {
        val section = section(
            matches = listOf(
                MATCH_A.copy(status = MatchStatus.REJECTED),
                MATCH_B.copy(status = MatchStatus.EXPIRED),
                MATCH_C.copy(status = MatchStatus.ACTIVE),
            ),
        )

        assertEquals("1 refusé · 1 sans réponse", section.outcomes)
    }

    @Test
    fun `rent - not round`() {
        val listings = mapOf(3012L to LISTING_A.copy(price = 152_500))

        val view = MatchViews.section(MANDATE, listOf(MATCH_A), listings, emptyMap(), LOCATIONS, TODAY, FORMATTER).matches[0]

        assertEquals("Loyer" to "152,5K\u00a0FCFA / mois", view.facts[2])
    }

    @Test
    fun `available now`() {
        val listings = mapOf(3012L to LISTING_A.copy(availableFrom = TODAY.minusDays(3)))

        val view = MatchViews.section(MANDATE, listOf(MATCH_A), listings, emptyMap(), LOCATIONS, TODAY, FORMATTER).matches[0]

        assertEquals("Disponibilité" to "Libre maintenant", view.facts[3])
    }

    @Test
    fun `available today`() {
        val listings = mapOf(3012L to LISTING_A.copy(availableFrom = TODAY))

        val view = MatchViews.section(MANDATE, listOf(MATCH_A), listings, emptyMap(), LOCATIONS, TODAY, FORMATTER).matches[0]

        assertEquals("Disponibilité" to "Libre maintenant", view.facts[3])
    }

    @Test
    fun `no availability date`() {
        val listings = mapOf(3012L to LISTING_A.copy(availableFrom = null))

        val view = MatchViews.section(MANDATE, listOf(MATCH_A), listings, emptyMap(), LOCATIONS, TODAY, FORMATTER).matches[0]

        assertEquals("Disponibilité" to "Libre maintenant", view.facts[3])
    }

    @Test
    fun `without neighbourhood nor rooms`() {
        val listings = mapOf(3012L to LISTING_A.copy(neighborhoodId = null, rooms = null))

        val view = MatchViews.section(MANDATE, listOf(MATCH_A), listings, emptyMap(), LOCATIONS, TODAY, FORMATTER).matches[0]

        assertEquals("Appartement", view.title)
        assertEquals("Quartier" to "Douala", view.facts[1])
    }

    @Test
    fun `one room`() {
        val listings = mapOf(3012L to LISTING_A.copy(rooms = 1))

        val view = MatchViews.section(MANDATE, listOf(MATCH_A), listings, emptyMap(), LOCATIONS, TODAY, FORMATTER).matches[0]

        assertEquals("Appartement · 1 chambre", view.title)
    }

    @Test
    fun `match without its listing`() {
        val listings = LISTINGS - 3027L

        val section = MatchViews.section(
            MANDATE,
            listOf(MATCH_A, MATCH_C, MATCH_B),
            listings,
            emptyMap(),
            LOCATIONS,
            TODAY,
            FORMATTER,
        )

        assertEquals(listOf(9001L, 9003L), section.matches.map { it.id })
    }

    @Test
    fun dismissed() {
        val matches = listOf(MATCH_A, MATCH_B.copy(status = MatchStatus.DISMISSED), MATCH_C.copy(status = MatchStatus.DISMISSED))

        val section = section(matches)

        assertEquals(listOf(9001L), section.matches.map { it.id })
        assertEquals(2, section.dismissed)
    }

    @Test
    fun `not actionable once proposed`() {
        val matches = listOf(MATCH_A.copy(status = MatchStatus.PROPOSED), MATCH_B.copy(status = MatchStatus.ACCEPTED))

        val section = section(matches)

        assertEquals(listOf(9003L, 9001L), section.matches.map { it.id })
        assertEquals(listOf(MatchGroup.INTERESTED, MatchGroup.WAITING), section.matches.map { it.group })
        assertFalse(section.matches.any { it.actionable })
        assertEquals("Envoyé", section.matches[1].answer)
        assertNull(section.matches[1].sent)
    }

    @Test
    fun `expired - actionable again`() {
        val matches = listOf(MATCH_A.copy(status = MatchStatus.EXPIRED))

        val section = section(matches)

        assertEquals(listOf(9001L), section.matches.map { it.id })
        assertEquals(MatchGroup.EXPIRED, section.matches[0].group)
        assertTrue(section.matches[0].actionable)
        assertEquals("Expirée", section.matches[0].tag.label)
        assertEquals("Envoyé, sans réponse", section.matches[0].answer)
        assertEquals("1 sans réponse", section.outcomes)
    }

    @Test
    fun `matches unavailable`() {
        val section = MatchViews.section(MANDATE, null, LISTINGS, emptyMap(), LOCATIONS, TODAY, FORMATTER)

        assertTrue(section.unavailable)
        assertTrue(section.matches.isEmpty())
    }

    @Test
    fun `listings unavailable`() {
        val section = MatchViews.section(MANDATE, listOf(MATCH_A), null, emptyMap(), LOCATIONS, TODAY, FORMATTER)

        assertTrue(section.unavailable)
        assertTrue(section.matches.isEmpty())
    }

    @Test
    fun empty() {
        val section = MatchViews.section(MANDATE, emptyList(), emptyMap(), emptyMap(), LOCATIONS, TODAY, FORMATTER)

        assertFalse(section.unavailable)
        assertTrue(section.matches.isEmpty())
        assertTrue(section.groups.isEmpty())
    }

    @Test
    fun `co-brokerage match - origin and back to the search`() {
        val section = section(listOf(MATCH_A.copy(coBrokerage = true)))

        val view = section.matches.single()
        assertEquals("/listings/3012?back=/mandates/204%23matches-title", view.listingUrl)
        assertEquals("Bien d'un autre agent", view.facts.first { it.first == "Origine" }.second)
    }

    @Test
    fun `own match - no origin and no back`() {
        val view = section(listOf(MATCH_A)).matches.single()

        assertEquals("/listings/3012", view.listingUrl)
        assertTrue(view.facts.none { it.first == "Origine" })
    }
}
