package com.wutsi.ndopify.web.ui.visit

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.visit.dto.Visit
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
    ) = VisitPages.of(visit, listing, images, locations, agent, customer, TENANT, FORMATTER, LocalDate.of(2026, 10, 31), cancel)

    @Test
    fun `requested`() {
        val page = of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE).copy(note = "Fin de journée"))

        assertEquals("Demandée", page.statusLabel)
        assertEquals("Sur place · samedi 7 novembre 2026 à 10:30", page.requestedSlot)
        assertEquals("Fin de journée", page.note)
        assertTrue(page.canCancel)
        assertNull(page.code)
        assertNull(page.address)
    }

    @Test
    fun `confirmed on site`() {
        val page = of(visit(VisitStatus.CONFIRMED, VisitType.ON_SITE))

        assertEquals("Confirmée", page.statusLabel)
        assertEquals("4827", page.code)
        assertEquals("Rue Njo-Njo, portail bleu", page.address)
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=4.0247,9.6943", page.mapUrl)
        assertTrue(page.canCancel)
    }

    @Test
    fun `confirmed video has the number, never the address`() {
        val page = of(visit(VisitStatus.CONFIRMED, VisitType.VIDEO), customer = Customer(whatsappNumber = "+237699887766"))

        assertNull(page.address)
        assertNull(page.mapUrl)
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
}
