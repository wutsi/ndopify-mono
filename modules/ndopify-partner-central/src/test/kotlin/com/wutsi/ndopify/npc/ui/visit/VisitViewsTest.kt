package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisitViewsTest {
    private fun of(
        visit: Visit = VisitFixtures.requested(),
        customer: Customer? = VisitFixtures.CUSTOMER,
        listing: Listing? = VisitFixtures.LISTING,
        now: Long = VisitFixtures.NOW,
    ) = VisitViews.of(
        visit,
        customer,
        listing,
        VisitFixtures.LOCATIONS,
        now,
        VisitFixtures.TENANT,
        VisitFixtures.FORMATTER,
    )

    @Test
    fun requested() {
        val page = of(VisitFixtures.requested())

        assertEquals(VisitRules.Panel.REQUESTED, page.panel)
        assertEquals("À confirmer", page.tag.label)
        assertEquals("warning", page.tag.style)
        assertEquals("Sur place", page.typeLabel)
        assertEquals("Sandrine Eboa", page.customerName)
        assertEquals("Sandrine", page.customerFirstName)
        assertEquals("+237 6 99 88 77 66", page.phone)
        assertEquals("237699887766", page.phoneDigits)
        assertEquals("Appartement 3 chambres · Bonapriso", page.listingTitle)
        assertEquals("Annonce n° 3012 · 240K\u00a0FCFA / mois", page.listingMeta)
        assertEquals("sam. 7 novembre 2026 à 10:30", page.requestedAt)
        assertEquals("Je viendrai avec mon mari.", page.note)
        assertEquals("42 h", page.expiresIn)
        assertTrue(page.canCancel)
        assertFalse(page.canCheckIn)
        assertEquals("2026-11-07", page.selectedDay)
        assertEquals("10:30", page.selectedTime)
        assertTrue(page.videoPossible)
    }

    @Test
    fun confirmed() {
        val page = of(VisitFixtures.confirmed())

        assertEquals(VisitRules.Panel.CONFIRMED, page.panel)
        assertEquals("Confirmée", page.tag.label)
        assertEquals("Rue Njo-Njo, portail bleu, Derrière la pharmacie", page.address)
        assertTrue(page.mapUrl!!.startsWith("https://www.openstreetmap.org/"))
        assertEquals("sam. 7 novembre 2026 à 09:30", page.windowFrom)
        assertTrue(page.canCancel)
        assertFalse(page.canCheckIn)
    }

    @Test
    fun window() {
        val page = of(VisitFixtures.window())

        assertEquals(VisitRules.Panel.WINDOW, page.panel)
        assertEquals("À démarrer", page.tag.label)
        assertEquals("info", page.tag.style)
        assertTrue(page.canCheckIn)
        assertTrue(page.canNoShow)
    }

    @Test
    fun `checked in`() {
        val page = of(VisitFixtures.checkedIn())

        assertEquals(VisitRules.Panel.CHECKED_IN, page.panel)
        assertEquals("En cours", page.tag.label)
        assertEquals("820 m", page.distance)
        assertTrue(page.distanceKnown)
        assertTrue(page.checkInFlagged)
        assertEquals("10:05", page.checkedInAt)
        assertEquals("10:15", page.checkOutFrom)
        assertTrue(page.checkOutReady)
        assertFalse(of(VisitFixtures.checkedIn(), now = VisitFixtures.checkedIn().checkedInAt!!.time + 9 * 60_000).checkOutReady)
    }

    @Test
    fun completed() {
        val page = of(VisitFixtures.completed())

        assertEquals(VisitRules.Panel.COMPLETED, page.panel)
        assertEquals("Terminée", page.tag.label)
        assertEquals("30 min", page.duration)
        assertEquals("À 820 m du bien (à vérifier par l'équipe Ndopify)", page.startDistance)
        assertEquals("Client intéressé, reviendra avec son épouse.", page.agentComment)
        assertEquals("Pas intéressé", page.feedbackTag)
        assertEquals("neutral", page.feedbackTone)
        assertEquals("trop cher", page.feedbackReason)
        assertEquals("Un peu cher pour le quartier.", page.feedbackComment)
    }

    @Test
    fun `cancelled by the agent before confirmation`() {
        val page = of(
            VisitFixtures.requested().copy(
                status = VisitStatus.CANCELLED,
                cancelledBy = VisitParty.AGENT,
                cancelledAt = VisitFixtures.date("2026-11-05T06:00:00Z"),
                cancellationReason = "Indisponible.",
            ),
        )

        assertEquals("Demande refusée", page.cancelledTitle)
        assertEquals("Vous avez refusé la demande le jeu. 5 novembre 2026 à 07:00.", page.cancelledText)
        assertEquals("« Indisponible. »", page.cancellationReason)
        assertEquals("sam. 7 novembre 2026 à 10:30", page.plannedAt)
    }

    @Test
    fun `cancelled by the agent after confirmation`() {
        val page = of(
            VisitFixtures.confirmed().copy(
                status = VisitStatus.CANCELLED,
                cancelledBy = VisitParty.AGENT,
                cancelledAt = VisitFixtures.date("2026-11-05T06:00:00Z"),
            ),
        )

        assertEquals("Visite annulée", page.cancelledTitle)
        assertEquals("Vous avez annulé la visite le jeu. 5 novembre 2026 à 07:00.", page.cancelledText)
    }

    @Test
    fun `cancelled by the customer`() {
        val request = of(
            VisitFixtures.requested().copy(
                status = VisitStatus.CANCELLED,
                cancelledBy = VisitParty.CUSTOMER,
                cancelledAt = VisitFixtures.date("2026-11-05T06:00:00Z"),
            ),
        )
        assertEquals("Demande annulée", request.cancelledTitle)
        assertEquals("Sandrine a annulé sa demande le jeu. 5 novembre 2026 à 07:00.", request.cancelledText)

        val visit = of(
            VisitFixtures.confirmed().copy(
                status = VisitStatus.CANCELLED,
                cancelledBy = VisitParty.CUSTOMER,
                cancelledAt = VisitFixtures.date("2026-11-05T06:00:00Z"),
            ),
        )
        assertEquals("Visite annulée", visit.cancelledTitle)
        assertEquals("Sandrine a annulé la visite le jeu. 5 novembre 2026 à 07:00.", visit.cancelledText)
    }

    @Test
    fun expired() {
        val page = of(VisitFixtures.requested().copy(status = VisitStatus.EXPIRED))

        assertEquals(VisitRules.Panel.EXPIRED, page.panel)
        assertEquals(
            "Vous n'avez pas confirmé la demande sous 48 h : elle a expiré le sam. 7 novembre 2026 à 05:00. " +
                "Sandrine peut en faire une nouvelle.",
            page.expiredText,
        )
    }

    @Test
    fun `no show`() {
        val agent = of(
            VisitFixtures.confirmed().copy(status = VisitStatus.NO_SHOW, noShowParty = VisitParty.AGENT),
        )
        assertEquals("Visite non démarrée", agent.noShowTitle)
        assertEquals(
            "Vous n'avez pas démarré la visite : elle est comptée comme une absence de votre part.",
            agent.noShowText,
        )

        val refused = of(
            VisitFixtures.confirmed().copy(
                status = VisitStatus.NO_SHOW,
                noShowParty = VisitParty.CUSTOMER,
                noShowReason = VisitNoShowReason.CODE_REFUSED,
            ),
        )
        assertEquals("Code refusé par le client", refused.noShowTitle)
        assertEquals("Déclaré par vous : l'absence du client est enregistrée.", refused.noShowText)

        val absentVideo = of(
            VisitFixtures.confirmed().copy(
                status = VisitStatus.NO_SHOW,
                type = VisitType.VIDEO,
                noShowParty = VisitParty.CUSTOMER,
                noShowReason = VisitNoShowReason.CUSTOMER_ABSENT,
            ),
        )
        assertEquals("Le client ne répond pas", absentVideo.noShowTitle)

        val absent = of(
            VisitFixtures.confirmed().copy(
                status = VisitStatus.NO_SHOW,
                noShowParty = VisitParty.CUSTOMER,
                noShowReason = VisitNoShowReason.CUSTOMER_ABSENT,
            ),
        )
        assertEquals("Client absent", absent.noShowTitle)
    }

    @Test
    fun video() {
        val page = of(VisitFixtures.requested().copy(type = VisitType.VIDEO))
        assertEquals("Visite vidéo", page.typeLabel)
        assertEquals("Rue Njo-Njo, portail bleu, Derrière la pharmacie", page.address)
        assertTrue(page.videoPossible)

        val noNumber = of(
            VisitFixtures.requested(),
            customer = VisitFixtures.CUSTOMER.copy(whatsappNumber = null),
        )
        assertFalse(noNumber.videoPossible)

        val video = of(
            VisitFixtures.requested().copy(type = VisitType.VIDEO),
            customer = VisitFixtures.CUSTOMER.copy(whatsappNumber = null),
        )
        assertTrue(video.videoPossible)
    }

    @Test
    fun `missing customer and listing`() {
        val page = of(VisitFixtures.requested(), customer = null, listing = null)

        assertEquals("Client", page.customerName)
        assertEquals("Le client", page.customerFirstName)
        assertNull(page.phone)
        assertNull(page.phoneDigits)
        assertNull(page.listingTitle)
        assertNull(page.listingMeta)
        assertNull(page.address)
        assertNull(page.mapUrl)
    }

    @Test
    fun `no code`() {
        val page = of(VisitFixtures.requested().copy(code = "4827"))

        assertFalse(page.toString().contains("4827"))
    }

    @Test
    fun `time zone`() {
        val page = of(VisitFixtures.requested().copy(requestedAt = VisitFixtures.date("2026-11-07T23:30:00Z")))

        assertEquals("dim. 8 novembre 2026 à 00:30", page.requestedAt)
    }
}
