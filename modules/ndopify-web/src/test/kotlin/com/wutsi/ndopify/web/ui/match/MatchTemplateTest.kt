package com.wutsi.ndopify.web.ui.match

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
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
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/match.html` with a real Thymeleaf engine. */
class MatchTemplateTest {
    /** Stands for [MatchController]: puts the given attributes in the model and renders `match`. */
    @Controller
    class FakeController {
        var attributes: Map<String, Any?> = emptyMap()

        @GetMapping("/match")
        fun page(model: Model): String {
            attributes.forEach { (key, value) -> model.addAttribute(key, value) }
            return MatchController.VIEW
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

    private val tenant = TENANT.copy(name = "Ndopify Cameroun", logoUrl = "https://cdn.ndopify.cm/logo.png")

    private val match = Match(
        id = 12,
        mandateId = 7,
        listingId = 123,
        status = MatchStatus.PROPOSED,
        priceScore = 1.0,
        distanceKm = 0.0,
        missingAmenities = listOf("generatorSet"),
    )
    private val mandate = Mandate(
        id = 7,
        cityId = 1,
        neighborhoodIds = listOf(5),
        propertyType = PropertyType.APARTMENT,
        rooms = "3",
        generatorSet = true,
        price = Money(250000, "XAF", PeriodicityType.MONTHLY),
    )
    private val agent = Agent(id = 3, party = Party(firstName = "Serge", lastName = "Ngono"), whatsappNumber = "+237677000111")
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
        securityDeposit = 2,
        title = "Appartement 3 chambres à Bonapriso",
        description = "Lumineux.",
        agentRemarks = "Propriétaire pressé de louer",
    )
    private val images = (1..3).map { ListingImage(id = it.toLong(), url = "https://cdn/$it.jpg", title = "Photo $it") }

    private fun page(
        status: MatchStatus = MatchStatus.PROPOSED,
        listing: Listing? = this.listing,
        agent: Agent = this.agent,
        answer: String? = null,
    ) = MatchPages.of(
        match.copy(status = status),
        mandate,
        agent,
        listing,
        images,
        listOf(Location(id = 1, name = "Douala"), Location(id = 5, name = "Bonapriso")),
        tenant,
        FORMATTER,
        LocalDate.of(2026, 10, 31),
        answer,
    )

    private fun html(page: MatchPage = page()): String {
        controller.attributes = mapOf("tenant" to tenant, "page" to page)
        return mvc.perform(get("/match")).andReturn().response.contentAsString
    }

    private fun render(page: MatchPage = page()): Document = Jsoup.parse(html(page))

    private fun answers(doc: Document) =
        doc.select("[data-answer], [data-bar-answer]").map { it.attr("data-answer").ifEmpty { it.attr("data-bar-answer") } }.distinct()

    @Test
    fun proposal() {
        val doc = render()

        assertEquals("Serge vous propose ce logement", doc.selectFirst("#proposal-title")?.text())
        assertEquals(
            "Pour votre recherche : appartement 3 chambres à Bonapriso, jusqu'à 250K FCFA / mois.",
            doc.selectFirst(".proposal__subtitle")?.text(),
        )
        assertEquals("SN", doc.selectFirst(".proposal__avatar")?.text())
        assertEquals(
            listOf("À Bonapriso, le quartier que vous cherchez", "240K FCFA / mois, dans votre budget", "Sans groupe électrogène"),
            doc.select(".proposal__fit li").map { it.text() },
        )
        assertNotNull(doc.selectFirst(".proposal__fit li.proposal__fit--partial"))
        assertEquals("Serge vous propose ce logement | Ndopify Cameroun", doc.title())
        assertEquals("noindex, nofollow", doc.selectFirst("meta[name=robots]")?.attr("content"))
        assertEquals("no-referrer", doc.selectFirst("meta[name=referrer]")?.attr("content"))
        assertEquals("https://cdn.ndopify.cm/logo.png", doc.selectFirst("header img")?.attr("src"))
    }

    @Test
    fun listing() {
        val doc = render()

        assertEquals("Appartement 3 chambres à Bonapriso", doc.selectFirst("h1")?.text())
        assertEquals("Bonapriso, Douala", doc.selectFirst(".listing__location")?.text())
        assertEquals(3, doc.select(".gallery__item").size)
        assertEquals("240K FCFA / mois", doc.selectFirst(".price-card__price")?.text())
        assertEquals("240K FCFA / mois", doc.selectFirst(".cta-bar__price")?.text())
        assertEquals("Lumineux.", doc.selectFirst("#description-heading ~ p")?.text())
        assertEquals("Serge Ngono", doc.selectFirst(".agent-card__name")?.text())
        assertEquals("https://wa.me/237677000111", doc.selectFirst(".agent-card a")?.attr("href"))
        assertTrue(doc.selectFirst("#listing-photos")!!.data().replace("\\/", "/").contains("https://cdn/3.jpg"))
    }

    @Test
    fun pending() {
        val doc = render()

        assertEquals(listOf("PENDING"), answers(doc))
        assertEquals("post", doc.selectFirst("form[action=/match/12/accept]")?.attr("method"))
        val reject = doc.selectFirst("#reject form[action=/match/12/reject]")
        assertNotNull(reject)
        assertEquals("post", reject.attr("method"))
        assertEquals(
            listOf("PRICE", "LOCATION", "SIZE", "AMENITIES", "AVAILABILITY", "OTHER"),
            reject.select("input[name=reason]").map { it.attr("value") },
        )
        assertEquals("500", reject.selectFirst("textarea[name=comment]")?.attr("maxlength"))
        // The price card's two answers, and the #answer popup's below 1024px
        assertEquals(2, doc.select("form[action=/match/12/accept]").size)
        assertEquals("#answer", doc.selectFirst(".cta-bar__actions a")?.attr("href"))
        assertNotNull(doc.selectFirst("#answer"))
        assertEquals(2, doc.select("a[href=#reject]").size)
        assertNull(doc.selectFirst("[data-state-badge]"))
        assertNull(doc.selectFirst(".proposal-flash"))
    }

    @Test
    fun accepted() {
        val doc = render(page(MatchStatus.ACCEPTED, answer = "accepted"))

        assertEquals(listOf("ACCEPTED"), answers(doc))
        assertTrue(doc.select("form").isEmpty())
        assertNull(doc.selectFirst("#answer"))
        assertNull(doc.selectFirst("#reject"))
        assertEquals("Vous intéresse", doc.selectFirst("[data-state-badge]")?.text())
        assertEquals("C'est noté : Serge est prévenu de votre intérêt.", doc.selectFirst(".proposal-flash")?.text())
    }

    @Test
    fun rejected() {
        val doc = render(page(MatchStatus.REJECTED))

        assertEquals(listOf("REJECTED"), answers(doc))
        assertTrue(doc.select("form").isEmpty())
        assertNull(doc.selectFirst("#reject"))
        assertEquals("Écarté", doc.selectFirst("[data-state-badge]")?.text())
        assertNull(doc.selectFirst(".proposal-flash"))
    }

    @Test
    fun unavailable() {
        val doc = render(page(listing = null))

        assertEquals(listOf("UNAVAILABLE"), answers(doc))
        assertTrue(doc.select("form").isEmpty())
        assertNull(doc.selectFirst(".gallery"))
        assertNull(doc.selectFirst("#listing-photos"))
        assertNull(doc.selectFirst(".price-card"))
        assertNull(doc.selectFirst(".proposal__why"))
        assertEquals("Ce logement n'est plus disponible.", doc.selectFirst("[data-answer=UNAVAILABLE] .answer__status")?.text())
        assertEquals("https://wa.me/237677000111", doc.selectFirst("[data-answer=UNAVAILABLE] a")?.attr("href"))
    }

    @Test
    fun `no WhatsApp link without number`() {
        val doc = render(page(listing = null, agent = agent.copy(whatsappNumber = null)))

        assertTrue(doc.select("a[href^=https://wa.me]").isEmpty())
        assertFalse(render(page(agent = agent.copy(whatsappNumber = null))).html().contains("wa.me"))
    }

    @Test
    fun `no visit request`() {
        val doc = render(page(MatchStatus.ACCEPTED))

        assertNull(doc.selectFirst("#visit"))
        assertNull(doc.selectFirst("#ask"))
        assertTrue(doc.select("a[href=#visit], a[href=#ask]").isEmpty())
    }

    @Test
    fun `never the agent's remarks`() {
        MatchStatus.entries.forEach { status ->
            assertFalse(html(page(status)).contains("Propriétaire pressé"), status.name)
        }
    }
}
