package com.wutsi.ndopify.web.ui.visit

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.listing.dto.ImageVariant
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitInterest
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
import kotlin.test.assertFalse
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
        landmark = "la boulangerie Zepol",
    )
    private val images = listOf(ListingImage(id = 1, url = "https://cdn/1.jpg", title = "Photo 1"))
    private val locations = listOf(Location(id = 1, name = "Douala"), Location(id = 5, name = "Bonapriso"))

    /** A SALE listing: a villa, 240M FCFA, no periodicity. */
    private val saleListing = listing.copy(
        listingType = ListingType.SALE,
        propertyType = PropertyType.VILLA,
        price = 240_000_000,
        pricePeriodicityType = PeriodicityType.NONE,
        title = "Villa 4 chambres à Bonapriso",
    )
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

    private fun of(
        visit: Visit,
        customer: Customer? = this.customer,
        agent: Agent? = this.agent,
        feedback: String? = null,
        images: List<ListingImage> = this.images,
        listing: Listing? = this.listing,
    ) = VisitPages.of(
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
        feedback,
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
    fun `the summary photo is the 240 variant`() {
        val variantImages = listOf(
            ListingImage(
                id = 1,
                url = "https://cdn/1.jpg",
                title = "Photo 1",
                variants = listOf(
                    ImageVariant(240, 160, "https://ik/1-240.jpg"),
                    ImageVariant(960, 640, "https://ik/1-960.jpg"),
                ),
            ),
        )
        val doc = render(of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE), images = variantImages))

        assertEquals("https://ik/1-240.jpg", doc.selectFirst(".visit-listing img")?.attr("src"))
    }

    @Test
    fun `confirmed on site`() {
        val doc = render(of(visit(VisitStatus.CONFIRMED, VisitType.ON_SITE)))
        assertEquals("4827", doc.selectFirst(".visit-code__digits span")?.text())
        assertEquals("Bonapriso, Douala", doc.selectFirst(".visit-address__landmark span")?.text())
        assertTrue(doc.selectFirst(".visit-address__landmark")!!.text().contains("près de la boulangerie Zepol"))
        assertTrue(
            doc.text().contains("L'adresse exacte et l'itinéraire vous seront communiqués par votre agent avant la visite."),
        )
        assertNull(doc.selectFirst(".visit-address"))
        assertNull(doc.selectFirst("a[href^=https://www.google.com/maps]"))
        assertNotNull(doc.selectFirst("#cancel"))
    }

    @Test
    fun `confirmed video`() {
        val doc = render(
            of(visit(VisitStatus.CONFIRMED, VisitType.VIDEO), customer = Customer(whatsappNumber = "+237699887766")),
        )
        assertEquals("Bonapriso, Douala", doc.selectFirst(".visit-address__landmark span")?.text())
        assertTrue(doc.selectFirst(".visit-address__landmark")!!.text().contains("près de la boulangerie Zepol"))
        assertFalse(doc.text().contains("et l'itinéraire"))
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
    fun `confirmed video without listing shows no empty landmark`() {
        val page = VisitPages.of(
            visit(VisitStatus.CONFIRMED, VisitType.VIDEO),
            null, images, locations, agent, Customer(whatsappNumber = "+237699887766"),
            tenant, FORMATTER, LocalDate.of(2026, 10, 31), null, null,
        )
        val doc = render(page)
        assertNull(doc.selectFirst(".visit-address__landmark"))
        assertTrue(doc.selectFirst("[data-visit-type=VIDEO]")!!.text().contains("+237699887766"))
    }

    @Test
    fun `cancelled without a party is neutral`() {
        val doc = render(of(visit(VisitStatus.CANCELLED, VisitType.ON_SITE).copy(cancelledBy = null)))
        assertTrue(doc.text().contains("La visite a été annulée."))
        assertFalse(doc.text().contains("a annulé la visite"))
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
    fun `requested promises no delay`() {
        val doc = render(of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE)))

        val notice = doc.selectFirst("[data-visit-state=REQUESTED] .notice")?.text()
        assertEquals("L'agent vous confirme la visite par e-mail : il peut vous proposer une autre heure.", notice)
    }

    @Test
    fun `expired says the agent did not confirm in time`() {
        val doc = render(of(visit(VisitStatus.EXPIRED, VisitType.ON_SITE)))

        val text = doc.selectFirst("[data-visit-state=EXPIRED]")!!.text()
        assertTrue(text.contains("Votre agent n'a pas confirmé votre demande à temps : elle a expiré."))
        assertFalse(text.contains("48"))
    }

    @Test
    fun `completed without feedback shows the form`() {
        val doc = render(of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE)))
        val completed = doc.selectFirst("[data-visit-state=COMPLETED]")!!

        assertTrue(completed.selectFirst("h2")!!.text().startsWith("Le bien vous intéresse"))
        assertEquals("post", completed.selectFirst("form.feedback")?.attr("method"))
        assertEquals("/visits/9/feedback", completed.selectFirst("form.feedback")?.attr("action"))
        assertEquals(2, completed.select("input[name=interest]").size)
        assertTrue(completed.select("input[name=interest]").all { it.hasAttr("required") })
        assertEquals(
            listOf("PRICE", "LOCATION", "SIZE", "AMENITIES", "AVAILABILITY", "OTHER"),
            completed.select("input[name=reasonCode]").map { it.attr("value") },
        )
        assertTrue(completed.select("input[name=reasonCode]").none { it.hasAttr("required") })
        assertEquals("1000", completed.selectFirst("#feedback-comment")?.attr("maxlength"))
        assertTrue(completed.text().contains("Votre visite sur place du samedi 7 novembre 2026 à 11:00 est terminée."))
        assertTrue(completed.text().contains("Votre réponse aide Serge Ngono pour la suite."))
        assertTrue(doc.select("[href$=.html], [src$=.html]").isEmpty())
    }

    @Test
    fun `completed interested shows the thanks and the agent whatsapp`() {
        val doc = render(of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(interest = VisitInterest.INTERESTED)))

        assertNull(doc.selectFirst("form.feedback"))
        assertEquals("Merci pour votre réponse", doc.selectFirst("[data-visit-state=COMPLETED] h2")?.text())
        assertTrue(
            doc.text().contains("Le logement vous intéresse : Serge Ngono vous recontacte pour la suite (dossier, bail)."),
        )
        assertEquals("https://wa.me/237677000111", doc.selectFirst(".feedback-thanks a.btn")?.attr("href"))
        assertTrue(doc.selectFirst(".feedback-thanks a.btn")!!.text().contains("Écrire à Serge Ngono"))
    }

    @Test
    fun `completed interested without a number has no whatsapp`() {
        val doc = render(
            of(
                visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(interest = VisitInterest.INTERESTED),
                agent = agent.copy(whatsappNumber = null),
            ),
        )

        assertNull(doc.selectFirst(".feedback-thanks a.btn"))
        assertNull(doc.selectFirst(".feedback-thanks"))
        assertFalse(doc.text().contains("Vous pouvez aussi lui écrire"))
    }

    @Test
    fun `completed not interested says the agent keeps searching`() {
        val doc = render(of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(interest = VisitInterest.NOT_INTERESTED)))

        assertNull(doc.selectFirst("form.feedback"))
        assertTrue(
            doc.text().contains("C'est noté : Serge Ngono est prévenu et continue de chercher pour vous. Il vous enverra d'autres logements."),
        )
    }

    @Test
    fun `feedback invalid shows the error above the form`() {
        val doc = render(of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE), feedback = VisitPages.FEEDBACK_INVALID))

        assertEquals("Répondez par oui ou par non.", doc.selectFirst(".field-error")?.text())
        assertNotNull(doc.selectFirst("form.feedback"))
    }

    @Test
    fun `other states have no feedback form`() {
        listOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN, VisitStatus.CANCELLED, VisitStatus.EXPIRED, VisitStatus.NO_SHOW)
            .forEach { status ->
                assertNull(render(of(visit(status, VisitType.ON_SITE))).selectFirst("form.feedback"), status.name)
            }
    }

    @Test
    fun `an open visit states the fee`() {
        val doc = render(of(visit(VisitStatus.CONFIRMED, VisitType.ON_SITE).copy(visitFee = Money(5000, "XAF"))))

        assertTrue(doc.selectFirst("[data-visit-fee]")!!.text().contains("5K FCFA"))
    }

    @Test
    fun `an ended visit states no fee`() {
        assertNull(
            render(of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(visitFee = Money(5000, "XAF"))))
                .selectFirst("[data-visit-fee]"),
        )
    }

    @Test
    fun `a sale visit's page speaks of the bien and the purchase`() {
        val doc = render(
            of(
                visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(interest = VisitInterest.INTERESTED),
                listing = saleListing,
            ),
        )

        assertTrue(doc.text().contains("Le bien vous intéresse : Serge Ngono vous recontacte pour la suite de votre achat."))
        assertEquals("240M FCFA", doc.selectFirst(".visit-listing__price")?.text())
        listOf("logement", "dossier", "bail").forEach { assertFalse(doc.text().contains(it), it) }
    }

    @Test
    fun `a sale visit's video block is the bien`() {
        val doc = render(of(visit(VisitStatus.CONFIRMED, VisitType.VIDEO), listing = saleListing))

        assertEquals("Le bien", doc.selectFirst("[data-visit-type=VIDEO] h3")?.text())
        assertFalse(doc.text().contains("logement"))
    }

    @Test
    fun `a sale visit's not interested thanks speak of other biens`() {
        val doc = render(
            of(
                visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(interest = VisitInterest.NOT_INTERESTED),
                listing = saleListing,
            ),
        )

        assertTrue(doc.text().contains("Il vous enverra d'autres biens."))
        assertFalse(doc.text().contains("logement"))
    }

    @Test
    fun `a sale visit's ended states speak of the bien`() {
        listOf(VisitStatus.CANCELLED, VisitStatus.EXPIRED, VisitStatus.NO_SHOW).forEach { status ->
            val doc = render(of(visit(status, VisitType.ON_SITE), listing = saleListing))

            assertTrue(doc.text().contains("Le bien vous intéresse toujours"), status.name)
            assertFalse(doc.text().contains("logement"), status.name)
        }
    }

    @Test
    fun `a sale visit's check-in e-mail is about the bien`() {
        val doc = render(of(visit(VisitStatus.CHECKED_IN, VisitType.ON_SITE), listing = saleListing))

        assertTrue(doc.text().contains("nous dire si le bien vous intéresse"))
        assertFalse(doc.text().contains("logement"))
    }
}
