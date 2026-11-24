package com.wutsi.ndopify.web.ui.visit

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.web.service.TenantFixtures.TENANT
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisitPagesTest {
    private val listing = Listing()
    private val images = emptyList<ListingImage>()
    private val locations = emptyList<Location>()
    private val agent = Agent(
        party = Party(firstName = "Serge", lastName = "Ngono", photoUrl = "https://cdn/agent.jpg"),
        whatsappNumber = "+237677000111",
    )
    private val customer = Customer(whatsappNumber = "+237699887766")

    private fun date(iso: String) = Date.from(Instant.parse(iso))

    private fun visit(status: VisitStatus, type: VisitType) = Visit(
        id = 9,
        matchId = 12,
        status = status,
        type = type,
        requestedAt = date("2026-11-07T09:30:00Z"),
        scheduledAt = date("2026-11-07T10:00:00Z"),
        code = "4827",
        address = "Rue Njo-Njo, portail bleu",
        latitude = 4.0247,
        longitude = 9.6943,
    )

    private fun of(
        visit: Visit,
        listing: Listing? = this.listing,
        agent: Agent? = this.agent,
        customer: Customer? = this.customer,
        cancel: String? = null,
        feedback: String? = null,
    ) = VisitPages.of(
        visit, listing, images, locations, agent, customer, TENANT, FORMATTER, LocalDate.of(2026, 10, 31), cancel, feedback,
    )

    @Test
    fun `requested`() {
        val page = of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE).copy(note = "Fin de journée"))

        assertEquals("Demandée", page.statusLabel)
        assertEquals("Sur place · samedi 7 novembre 2026 à 10:30", page.requestedSlot)
        assertEquals("Fin de journée", page.note)
        assertTrue(page.canCancel)
        assertNull(page.code)
    }

    @Test
    fun `confirmed on site`() {
        val page = of(visit(VisitStatus.CONFIRMED, VisitType.ON_SITE))

        assertEquals("Confirmée", page.statusLabel)
        assertEquals("4827", page.code)
        assertTrue(page.canCancel)
    }

    @Test
    fun `confirmed video has the number`() {
        val page = of(visit(VisitStatus.CONFIRMED, VisitType.VIDEO), customer = Customer(whatsappNumber = "+237699887766"))

        assertEquals("+237699887766", page.videoNumber)
    }

    @Test
    fun `changed slot`() {
        assertEquals(
            "Vous aviez demandé samedi 7 novembre 2026 à 10:30 : l'agent a proposé un autre créneau.",
            of(visit(VisitStatus.CONFIRMED, VisitType.ON_SITE)).changedSlot,
        )
    }

    @Test
    fun `checked in cannot cancel`() {
        val page = of(visit(VisitStatus.CHECKED_IN, VisitType.ON_SITE))
        assertFalse(page.canCancel)
        assertEquals("En cours", page.statusLabel)
    }

    @Test
    fun `checked in keeps the code`() {
        val page = of(visit(VisitStatus.CHECKED_IN, VisitType.ON_SITE))

        assertEquals("4827", page.code)
    }

    @Test
    fun `ended states`() {
        assertEquals("Terminée", of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE)).statusLabel)
        assertEquals("Annulée", of(visit(VisitStatus.CANCELLED, VisitType.ON_SITE)).statusLabel)
        assertEquals("Expirée", of(visit(VisitStatus.EXPIRED, VisitType.ON_SITE)).statusLabel)
        assertEquals("Non effectuée", of(visit(VisitStatus.NO_SHOW, VisitType.ON_SITE)).statusLabel)
        assertFalse(of(visit(VisitStatus.CANCELLED, VisitType.ON_SITE)).canCancel)
    }

    @Test
    fun `cancel flashes`() {
        val cancelled = visit(VisitStatus.CANCELLED, VisitType.ON_SITE).copy(cancelledBy = VisitParty.CUSTOMER)
        assertEquals("Votre visite est annulée. L'agent est prévenu.", of(cancelled, cancel = "done").flash)
        assertEquals("Cette visite ne peut plus être annulée.", of(cancelled, cancel = "conflict").flash)
        assertNull(of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE), cancel = "done").flash)
    }

    @Test
    fun `listing and agent are optional`() {
        val page = of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE), listing = null, agent = null)
        assertNull(page.listing)
        assertNull(page.agent)
    }

    @Test
    fun `completed intro`() {
        assertEquals(
            "Votre visite sur place du samedi 7 novembre 2026 à 11:00 est terminée.",
            of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE)).feedbackIntro,
        )
    }

    @Test
    fun `completed video intro`() {
        assertEquals(
            "Votre visite vidéo du samedi 7 novembre 2026 à 11:00 est terminée.",
            of(visit(VisitStatus.COMPLETED, VisitType.VIDEO)).feedbackIntro,
        )
    }

    @Test
    fun `feedback interest is the answer`() {
        val page = of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(interest = VisitInterest.NOT_INTERESTED))

        assertEquals(VisitInterest.NOT_INTERESTED, page.feedbackInterest)
    }

    @Test
    fun `feedback invalid asks for an answer`() {
        val page = of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE), feedback = VisitPages.FEEDBACK_INVALID)

        assertEquals("Répondez par oui ou par non.", page.feedbackError)
    }

    @Test
    fun `feedback invalid is ignored once answered`() {
        val page = of(
            visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(interest = VisitInterest.INTERESTED),
            feedback = VisitPages.FEEDBACK_INVALID,
        )

        assertNull(page.feedbackError)
    }

    @Test
    fun `no feedback error outside COMPLETED`() {
        val page = of(visit(VisitStatus.CHECKED_IN, VisitType.ON_SITE), feedback = VisitPages.FEEDBACK_INVALID)

        assertNull(page.feedbackError)
        assertNull(page.feedbackIntro)
    }

    @Test
    fun `an open visit states the fee`() {
        listOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN).forEach { status ->
            val page = of(visit(status, VisitType.ON_SITE).copy(visitFee = Money(5000, "XAF")))

            assertTrue(page.visitFee!!.replace('\u00a0', ' ').contains("5K FCFA"), status.name)
            assertTrue(page.visitFee!!.contains("Ndopify ne perçoit aucun paiement"), status.name)
        }
    }

    @Test
    fun `an ended visit states no fee`() {
        listOf(VisitStatus.COMPLETED, VisitStatus.CANCELLED, VisitStatus.EXPIRED, VisitStatus.NO_SHOW).forEach { status ->
            assertNull(of(visit(status, VisitType.ON_SITE).copy(visitFee = Money(5000, "XAF"))).visitFee)
        }
    }

    @Test
    fun `no fee`() {
        assertEquals("Aucun frais de visite.", of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE)).visitFee)
    }

    @Test
    fun `a video visit states the same fee`() {
        val page = of(visit(VisitStatus.CONFIRMED, VisitType.VIDEO).copy(visitFee = Money(5000, "XAF")))

        assertTrue(page.visitFee!!.replace('\u00a0', ' ').contains("5K FCFA"))
    }

    @Test
    fun `a sale match's visit is worded for a buyer`() {
        val page = of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE), listing = Listing(listingType = ListingType.SALE))

        assertTrue(page.sale)
    }

    @Test
    fun `a rental match's visit keeps the rental wording`() {
        assertFalse(of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE)).sale)
    }

    @Test
    fun `an unreadable listing counts as a rental`() {
        assertFalse(of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE), listing = null).sale)
    }
}
