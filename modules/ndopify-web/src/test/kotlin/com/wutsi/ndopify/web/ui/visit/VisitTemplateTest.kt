package com.wutsi.ndopify.web.ui.visit

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.web.service.TenantFixtures.TENANT
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Controller
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/visit.html` with a real Thymeleaf engine. */
class VisitTemplateTest {
    /** Stands for [VisitController]: puts the given attributes in the model and renders `visit`. */
    @Controller
    class FakeController {
        var attributes: Map<String, Any?> = emptyMap()

        @GetMapping("/visits/{id}")
        fun page(model: Model): String {
            attributes.forEach { (key, value) -> model.addAttribute(key, value) }
            return VisitController.VIEW
        }
    }

    private val controller = FakeController()
    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(controller)
        .setViewResolvers(
            ThymeleafViewResolver().apply {
                templateEngine = SpringTemplateEngine().apply {
                    setTemplateResolver(
                        ClassLoaderTemplateResolver().apply {
                            prefix = "templates/"
                            suffix = ".html"
                            characterEncoding = "UTF-8"
                        },
                    )
                }
                characterEncoding = "UTF-8"
            },
        )
        .build()

    private val tenant = TENANT.copy(
        name = "Ndopify Cameroun",
        logoUrl = "https://cdn.ndopify.cm/logo.png",
        iconUrl = "https://cdn.ndopify.cm/icon.png",
    )

    private val listing = Listing(
        id = 123,
        status = ListingStatus.PUBLISHED,
        cityId = 1,
        neighborhoodId = 5,
        propertyType = PropertyType.APARTMENT,
        rooms = 3,
        price = 240000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
        title = "Appartement 3 chambres à Bonapriso",
    )
    private val images = listOf(ListingImage(id = 1, url = "https://cdn/1.jpg", title = "Photo 1"))
    private val locations = listOf(Location(id = 1, name = "Douala"), Location(id = 5, name = "Bonapriso"))
    private val agent = Agent(id = 3, party = Party(firstName = "Serge", lastName = "Ngono"), whatsappNumber = "+237677000111")
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

    private fun of(visit: Visit, customer: Customer? = this.customer) = VisitPages.of(
        visit,
        listing,
        images,
        locations,
        agent,
        customer,
        tenant,
        FORMATTER,
        LocalDate.of(2026, 10, 31),
        null,
    )

    private fun render(page: VisitPage): Document {
        controller.attributes = mapOf("tenant" to tenant, "page" to page)
        return Jsoup.parse(mvc.perform(get("/visits/9")).andReturn().response.contentAsString)
    }

    @Test
    fun requested() {
        val doc = render(of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE).copy(note = "Fin de journée")))

        assertEquals("Demandée", doc.selectFirst(".visit-badge")?.text())
        assertEquals("Votre demande est envoyée", doc.selectFirst("[data-visit-state=REQUESTED] h2")?.text())
        assertEquals("Fin de journée", doc.selectFirst(".visit-facts__note")?.text())
        assertEquals("post", doc.selectFirst("#cancel form")?.attr("method"))
        assertEquals("/visits/9/cancel", doc.selectFirst("#cancel form")?.attr("action"))
        assertEquals("500", doc.selectFirst("#cancel textarea")?.attr("maxlength"))
    }

    @Test
    fun `confirmed on site`() {
        val doc = render(of(visit(VisitStatus.CONFIRMED, VisitType.ON_SITE)))
        assertEquals("4827", doc.selectFirst(".visit-code__digits span")?.text())
        assertEquals("Rue Njo-Njo, portail bleu", doc.selectFirst(".visit-address")?.text())
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=4.0247,9.6943",
            doc.selectFirst("a[href^=https://www.google.com/maps]")?.attr("href"),
        )
        assertNotNull(doc.selectFirst("#cancel"))
    }

    @Test
    fun `confirmed video`() {
        val doc = render(
            of(visit(VisitStatus.CONFIRMED, VisitType.VIDEO), customer = Customer(whatsappNumber = "+237699887766")),
        )
        assertNull(doc.selectFirst(".visit-address"))
        assertTrue(doc.selectFirst("[data-visit-type=VIDEO]")!!.text().contains("+237699887766"))
    }

    @Test
    fun `ended states have no cancel`() {
        listOf(VisitStatus.CHECKED_IN, VisitStatus.COMPLETED, VisitStatus.CANCELLED, VisitStatus.EXPIRED, VisitStatus.NO_SHOW).forEach { status ->
            assertNull(render(of(visit(status, VisitType.ON_SITE))).selectFirst("#cancel"), status.name)
        }
    }

    @Test
    fun `cancelled by the agent shows the reason`() {
        val doc = render(
            of(
                visit(VisitStatus.CANCELLED, VisitType.ON_SITE).copy(
                    cancelledBy = VisitParty.AGENT,
                    cancellationReason = "Le propriétaire n'est pas disponible.",
                ),
            ),
        )
        assertTrue(doc.text().contains("a annulé la visite"))
        assertTrue(doc.text().contains("Le propriétaire n'est pas disponible."))
    }

    @Test
    fun `no feedback form and no prototype links`() {
        val doc = render(of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE)))
        assertNull(doc.selectFirst("[name=interest]"))
        assertTrue(doc.select("[href$=.html], [src$=.html]").isEmpty())
    }
}
