package com.wutsi.ndopify.web.ui.listing

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.refdata.dto.Location
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
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/listing.html` with a real Thymeleaf engine. */
class ListingTemplateTest {
    /** Stands for [ListingController]: puts the given attributes in the model and renders `listing`. */
    @Controller
    class FakeController {
        var attributes: Map<String, Any?> = emptyMap()

        @GetMapping("/listing")
        fun page(model: Model): String {
            attributes.forEach { (key, value) -> model.addAttribute(key, value) }
            return ListingController.VIEW
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
        publicPortalUrl = "https://www.ndopify.cm",
    )

    private val listing = Listing(
        id = 123,
        listingType = ListingType.RENTAL,
        cityId = 1,
        neighborhoodId = 2,
        propertyType = PropertyType.APARTMENT,
        furnitureType = FurnitureType.FULLY_FURNISHED,
        rooms = 2,
        bathrooms = 2,
        area = 95,
        parkings = 1,
        waterWell = true,
        price = 175000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
        securityDeposit = 2,
        title = "Appartement meublé 2 chambres à Bonapriso",
        summary = "Un bel appartement.",
        description = "Lumineux.\n\nCalme.",
        url = "/douala/bonapriso/listing/123/appartement",
        verifiedAt = Date(),
    )

    private val images = (1..8).map { ListingImage(id = it.toLong(), url = "https://cdn/$it.jpg", title = "Photo $it") }

    private fun page(listing: Listing = this.listing, images: List<ListingImage> = this.images) = ListingPages.of(
        listing,
        images,
        listOf(Location(id = 1, name = "Douala"), Location(id = 2, name = "Bonapriso")),
        tenant,
        FORMATTER,
        LocalDate.of(2026, 10, 30),
    )

    private fun render(page: ListingPage = page()): Document {
        controller.attributes = mapOf("tenant" to tenant, "page" to page)
        return Jsoup.parse(mvc.perform(get("/listing")).andReturn().response.contentAsString)
    }

    @Test
    fun `listing content`() {
        val doc = render()

        assertEquals("Appartement meublé 2 chambres à Bonapriso", doc.selectFirst("h1")?.text())
        assertEquals("Appartement meublé 2 chambres à Bonapriso | Ndopify Cameroun", doc.title())
        assertEquals("Un bel appartement.", doc.selectFirst("meta[name=description]")?.attr("content"))
        assertEquals("https://www.ndopify.cm/douala/bonapriso/listing/123/appartement", doc.selectFirst("link[rel=canonical]")?.attr("href"))
        assertTrue(doc.selectFirst(".price-card__price")!!.text().contains("175K"))
        assertEquals("Bonapriso, Douala", doc.selectFirst(".listing__location")?.text())
        assertEquals(5, doc.select(".key-facts li").size)
        assertEquals("2 chambres", doc.select(".key-facts li").first()?.text())
        assertEquals(listOf("Lumineux.", "Calme."), doc.select("#description-heading ~ p").map { it.text() })
        assertEquals("Forage", doc.selectFirst(".check-list li")?.text())
        assertEquals("Caution", doc.select(".terms-table th")[1].text())
        assertNotNull(doc.selectFirst(".audit-note"))
        assertEquals("https://cdn.ndopify.cm/logo.png", doc.selectFirst("header img")?.attr("src"))
        val ld = doc.selectFirst("script[type=application/ld+json]")!!.data()
        assertTrue(ld.contains("\"@type\": \"Apartment\""))
        assertTrue(ld.contains("\"numberOfRooms\": 2"))
        assertTrue(ld.replace(" ", "").contains("\"floorSize\":{\"@type\":\"QuantitativeValue\",\"value\":95,\"unitCode\":\"MTK\"}"), ld)
        assertTrue(ld.contains("\"geo\": null"), ld)
    }

    @Test
    fun `gallery and photo viewer`() {
        val doc = render()

        assertEquals(5, doc.select(".gallery__item").size)
        assertEquals("https://cdn/1.jpg", doc.selectFirst(".gallery__item--hero")?.attr("href"))
        assertEquals("Photo 1", doc.selectFirst(".gallery__item--hero img")?.attr("alt"))
        assertEquals("Voir les 8 photos", doc.selectFirst(".gallery__all")?.text())
        assertTrue(doc.selectFirst("#listing-photos")!!.data().replace("\\/", "/").contains("https://cdn/8.jpg"))
        assertEquals("https://cdn/1.jpg", doc.selectFirst("meta[property=og:image]")?.attr("content"))
    }

    @Test
    fun `no "see all" link with 5 photos or less`() {
        val doc = render(page(images = images.take(3)))

        assertEquals(3, doc.select(".gallery__item").size)
        assertNull(doc.selectFirst(".gallery__all"))
    }

    @Test
    fun `no gallery without photo`() {
        val doc = render(page(images = emptyList()))

        assertNull(doc.selectFirst(".gallery"))
        assertEquals("https://cdn.ndopify.cm/logo.png", doc.selectFirst("meta[property=og:image]")?.attr("content"))
    }

    @Test
    fun `sections left out when empty`() {
        val doc = render(page(listing.copy(description = null, waterWell = false, verifiedAt = null, landmark = null)))

        assertNull(doc.selectFirst("#description-heading"))
        assertNull(doc.selectFirst("#amenities-heading"))
        assertNull(doc.selectFirst(".audit-note"))
    }

    @Test
    fun `description is escaped`() {
        val doc = render(page(listing.copy(description = "<b>x</b>")))

        assertEquals("<b>x</b>", doc.selectFirst("#description-heading ~ p")?.text())
        assertNull(doc.selectFirst("#description-heading ~ p b"))
    }

    @Test
    fun `visit days and times`() {
        val doc = render()

        val days = doc.select("input[name=visitDate]")
        assertEquals(8, days.size)
        assertEquals("2026-10-31", days.first()?.attr("value"))
        assertEquals("sam. 31 oct.", days.first()?.parent()?.text())
        assertEquals(23, doc.select("#visit-time option[value~=.+]").size)
        assertEquals("10h00", doc.select("#visit-time option[value~=.+]").first()?.text())
    }

    @Test
    fun `visit and question forms are the prototype's`() {
        val doc = render()

        assertEquals("#visit", doc.selectFirst("form[data-interest-form]")?.attr("action"))
        assertEquals("#ask", doc.selectFirst("form[data-chat-form]")?.attr("action"))
        assertNotNull(doc.selectFirst("script[src=/assets/js/listing.js]"))
        assertNotNull(doc.selectFirst("link[href=/assets/css/listing.css]"))
    }

    @Test
    fun `no link to the prototype`() {
        val doc = render()

        assertTrue(doc.select("[href$=.html], [src$=.html]").isEmpty())
    }
}
