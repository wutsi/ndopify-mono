package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.LOCATIONS
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.NOW
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.TENANT
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.checkedIn
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.confirmed
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.customerSummary
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.date
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.listingSummary
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.requested
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.summary
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.window
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.visit.dto.VisitType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisitCardsTest {
    private fun of(
        visits: List<VisitSummary>,
        listings: Map<Long, ListingSummary>? = mapOf(3012L to listingSummary()),
        customers: Map<Long, CustomerSummary> = mapOf(300L to customerSummary()),
        now: Long = NOW,
    ) = VisitCards.of(visits, listings, customers, LOCATIONS, now, TENANT, VisitFixtures.FORMATTER)

    @Test
    fun `requested card`() {
        val cards = of(listOf(summary(VisitFixtures.requested())))

        val card = cards.requested.single()
        assertEquals(7001L, card.id)
        assertEquals("Sandrine Eboa", card.customerName)
        assertEquals("Sur place", card.type)
        assertNull(card.tag)
        assertEquals("sam. 7 novembre 2026 à 10:30", card.whenLabel)
        assertEquals("Sur place · Appartement 3 chambres · Bonapriso", card.listing)
        assertEquals("42 h", card.expiresIn)
        assertFalse(card.urgent)
        assertTrue(cards.upcoming.isEmpty())
        assertFalse(cards.unavailable)
    }

    @Test
    fun `requested - urgent`() {
        val visit = summary(requested().copy(createdAt = date("2026-11-03T20:00:00Z")))

        val card = of(listOf(visit)).requested.single()

        assertTrue(card.urgent)
        assertEquals("10 h", card.expiresIn)
    }

    @Test
    fun `upcoming - confirmed and window`() {
        val cards = of(listOf(summary(confirmed()), summary(window())))

        assertEquals(listOf(7001L, 7001L), cards.upcoming.map { it.id })
        val window = cards.upcoming[0]
        assertEquals("À démarrer", window.tag?.label)
        assertEquals("info", window.tag?.style)
        assertEquals("Aujourd'hui · 10:30", window.whenLabel)
        assertNull(window.expiresAt)
        assertNull(window.expiresIn)
        assertFalse(window.urgent)

        val confirmed = cards.upcoming[1]
        assertEquals("Confirmée", confirmed.tag?.label)
        assertEquals("sam. 7 novembre 2026 à 10:30", confirmed.whenLabel)
        assertTrue(cards.requested.isEmpty())
    }

    @Test
    fun `upcoming - checked in`() {
        val card = of(listOf(summary(checkedIn()))).upcoming.single()

        assertEquals("En cours depuis 10:05", card.whenLabel)
        assertEquals("En cours", card.tag?.label)
    }

    @Test
    fun `stale requested and confirmed are dropped`() {
        val staleRequest = summary(requested().copy(createdAt = date("2026-11-03T09:00:00Z")))
        val staleConfirmed = summary(confirmed().copy(scheduledAt = date("2026-11-05T05:00:00Z")))

        val cards = of(listOf(staleRequest, staleConfirmed))

        assertTrue(cards.requested.isEmpty())
        assertTrue(cards.upcoming.isEmpty())
    }

    @Test
    fun `empty`() {
        val cards = of(emptyList())

        assertTrue(cards.requested.isEmpty())
        assertTrue(cards.upcoming.isEmpty())
        assertFalse(cards.unavailable)
    }

    @Test
    fun `visits unavailable`() {
        val cards = VisitCards.of(null, null, emptyMap(), LOCATIONS, NOW, TENANT, VisitFixtures.FORMATTER)

        assertTrue(cards.unavailable)
        assertTrue(cards.requested.isEmpty())
        assertTrue(cards.upcoming.isEmpty())
    }

    @Test
    fun `video visit`() {
        val card = of(listOf(summary(requested().copy(type = com.wutsi.ndopify.visit.dto.VisitType.VIDEO)))).requested.single()

        assertEquals("Visite vidéo", card.type)
    }

    @Test
    fun `missing listing`() {
        val card = of(listOf(summary(requested())), listings = emptyMap()).requested.single()

        assertEquals("Sur place · Annonce n° 3012", card.listing)
    }

    @Test
    fun `missing customer`() {
        val card = of(listOf(summary(requested())), customers = emptyMap()).requested.single()

        assertEquals("Client", card.customerName)
    }

    @Test
    fun `requested card carries its match, state and block wording`() {
        val card = of(listOf(summary(requested()))).requested.single()

        assertEquals(9001L, card.matchId)
        assertEquals(VisitStatus.REQUESTED, card.status)
        assertFalse(card.video)
        assertEquals("Visite demandée", card.kindLabel)
        assertEquals("pour le sam. 7 novembre 2026 à 10:30", card.whenText)
        assertEquals(requested().requestedAt!!.time, card.at)
    }

    @Test
    fun `video request names the video state`() {
        val card = of(listOf(summary(requested().copy(type = VisitType.VIDEO)))).requested.single()

        assertTrue(card.video)
        assertEquals("Visite vidéo demandée", card.kindLabel)
    }

    @Test
    fun `confirmed card says when the visit is`() {
        val card = of(listOf(summary(confirmed()))).upcoming.single()

        assertEquals(VisitStatus.CONFIRMED, card.status)
        assertEquals("Visite confirmée", card.kindLabel)
        assertEquals("le sam. 7 novembre 2026 à 10:30", card.whenText)
    }

    @Test
    fun `checked in card says since when`() {
        val card = of(listOf(summary(checkedIn()))).upcoming.single()

        assertEquals("Visite en cours", card.kindLabel)
        assertEquals("depuis 10:05", card.whenText)
    }

    @Test
    fun `requested card carries its inline confirmation slots`() {
        val card = of(listOf(summary(requested()))).requested.single()

        assertEquals(7, card.days.size)
        assertEquals(25, card.times.size)
        assertEquals("2026-11-07", card.selectedDay)
        assertEquals("10:30", card.selectedTime)
        assertTrue(card.requestedSlotValid)
    }

    @Test
    fun `requested slot outside the window falls back to the first slot`() {
        val card = of(listOf(summary(requested().copy(requestedAt = date("2026-11-20T09:30:00Z"))))).requested.single()

        assertFalse(card.requestedSlotValid)
        assertEquals("2026-11-06", card.selectedDay)
        assertEquals("10:30", card.selectedTime)
    }

    @Test
    fun `upcoming visits are grouped by day`() {
        val cards = of(
            listOf(
                summary(confirmed().copy(id = 7002, scheduledAt = date("2026-11-05T12:00:00Z"))),
                summary(confirmed().copy(id = 7003, scheduledAt = date("2026-11-06T12:00:00Z"))),
                summary(confirmed().copy(id = 7004, scheduledAt = date("2026-11-09T12:00:00Z"))),
            ),
        )

        assertEquals(listOf("Aujourd'hui", "Demain", "lun. 9 novembre"), cards.upcomingGroups.map { it.label })
    }

    @Test
    fun `upcoming visits are capped at four, the rest folded`() {
        val visits = (1..6).map { day ->
            summary(
                confirmed().copy(
                    id = 7000L + day,
                    scheduledAt = date("2026-11-${(5 + day).toString().padStart(2, '0')}T12:00:00Z"),
                ),
            )
        }

        val cards = of(visits)

        assertEquals(6, cards.upcoming.size)
        assertEquals(4, cards.upcomingPreviewGroups.sumOf { it.visits.size })
        assertEquals(2, cards.upcomingMoreCount)
        assertEquals(2, cards.upcomingMoreGroups.sumOf { it.visits.size })
    }

    @Test
    fun `checked-in visits are separated from the upcoming ones`() {
        val cards = of(
            listOf(
                summary(checkedIn().copy(id = 7002)),
                summary(confirmed().copy(id = 7003)),
            ),
        )

        assertEquals(7002L, cards.ongoing.single().id)
        assertEquals(listOf(7002L, 7003L), cards.upcoming.map { it.id })
    }
}
