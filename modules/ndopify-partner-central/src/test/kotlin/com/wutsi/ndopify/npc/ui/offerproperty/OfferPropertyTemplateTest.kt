package com.wutsi.ndopify.npc.ui.offerproperty

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.npc.ui.findproperty.SubmittedMandate
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpServerErrorException
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Renders `templates/offer-property.html` through [OfferPropertyController] with a real Thymeleaf engine, to catch
 * template mistakes (expressions, conditions, pre-filled values) that [OfferPropertyControllerTest] can't see.
 */
class OfferPropertyTemplateTest {
    private val mandateClient = mock<NdMandateClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var session: MockHttpSession
    private lateinit var mvc: MockMvc

    private val clock = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC)

    private val locations = listOf(
        Location(id = 2220957, name = "Yaoundé", type = LocationType.CITY, country = "CM"),
        Location(id = 1001, parentId = 2220957, name = "Bastos", type = LocationType.NEIGHBORHOOD, country = "CM"),
    )

    private val complete = PendingListingTest.COMPLETE

    private val tenant = TENANT.copy(
        name = "Ndopify Cameroun",
        logoUrl = "https://cdn.ndopify.com/tenant/1/logo.png",
        publicPortalUrl = "https://www.ndopify.com",
    )

    companion object {
        const val OWNER = "https://www.ndopify.com/owner"
        const val STEPS = "property,address,terms,features,amenities,contact,review"
    }

    @BeforeEach
    fun setUp() {
        doReturn(tenant).whenever(tenantContext).getTenant()
        whenever(locationClient.search(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<SearchLocationRequest>(0)
            SearchLocationResponse(locations = locations.filter { request.ids.contains(it.id) })
        }

        val engine = SpringTemplateEngine().apply {
            setTemplateResolver(
                ClassLoaderTemplateResolver().apply {
                    prefix = "templates/"
                    suffix = ".html"
                    characterEncoding = "UTF-8"
                },
            )
        }
        val viewResolver = ThymeleafViewResolver().apply {
            templateEngine = engine
            characterEncoding = "UTF-8"
        }
        mvc = MockMvcBuilders
            .standaloneSetup(OfferPropertyController(mandateClient, locationClient, tenantContext, clock))
            .setViewResolvers(viewResolver)
            .build()
        session = MockHttpSession()
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)
    }

    @ParameterizedTest
    @ValueSource(strings = ["property", "address", "terms", "features", "amenities", "contact", "review"])
    fun `every step has a Quitter button to the owner page`(step: String) {
        val page = render(get("/offer-property?step=$step"))

        val exit = assertNotNull(page.selectFirst("a[data-exit]"))
        assertEquals("Quitter", exit.text())
        assertEquals(OWNER, exit.attr("href"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["property", "address", "terms", "features", "amenities", "contact", "review"])
    fun `every step posts to its own action`(step: String) {
        val page = render(get("/offer-property?step=$step"))

        val form = page.select("form[data-offer-form]").single()
        assertEquals("post", form.attr("method"))
        assertEquals("/offer-property/$step", form.attr("action"))
        assertEquals(1, page.select("form[action=/offer-property/$step]").size)
        assertEquals(listOf(step), page.select("[data-step]").map { it.attr("data-step") })
        assertEquals("Étape ${STEPS.split(",").indexOf(step) + 1} sur 7", page.selectFirst("[data-progress-label]")?.text())
    }

    @Test
    fun `confirmation CTA goes to the owner page`() {
        val page = confirmation()

        assertEquals("Quitter", page.selectFirst("a[data-exit]")?.text())
        assertEquals(OWNER, page.selectFirst("a[data-exit]")?.attr("href"))
        val cta = assertNotNull(page.selectFirst(".cta-row a.btn--primary"))
        assertEquals("Retour à l'accueil", cta.text())
        assertEquals(OWNER, cta.attr("href"))
    }

    @Test
    fun `no Quitter nor CTA without public portal`() {
        doReturn(TENANT).whenever(tenantContext).getTenant()

        assertNull(render(get("/offer-property?step=property")).selectFirst("a[data-exit]"))
        val page = confirmation()
        assertNull(page.selectFirst("a[data-exit]"))
        assertNull(page.selectFirst(".cta-row a"))
        assertEquals("/assets/images/logo.png", page.selectFirst(".site-header__brand img")?.attr("src"))
    }

    @Test
    fun `header shows the tenant's logo`() {
        val logo = render(get("/offer-property?step=property")).selectFirst(".site-header__brand img")!!

        assertEquals(tenant.logoUrl, logo.attr("src"))
        assertEquals(tenant.name, logo.attr("alt"))
    }

    @Test
    fun `property step has no previous button`() {
        session.removeAttribute(PendingListing.SESSION_ATTRIBUTE)

        val page = render(get("/offer-property"))

        assertEquals("Proposer un bien — Ndopify", page.title())
        assertNull(page.selectFirst("[data-prev]"))
        assertEquals("Suivant", page.selectFirst("[data-next]")?.text())
        assertEquals(listOf("RESIDENTIAL", "COMMERCIAL"), page.select("input[name=propertyCategory]").map { it.attr("value") })
        assertTrue(page.select("input[name=propertyCategory][checked]").isEmpty())
    }

    @Test
    fun `other steps link to the previous one`() {
        assertEquals("/offer-property", render(get("/offer-property?step=address")).selectFirst("a[data-prev]")?.attr("href"))
        assertEquals(
            "/offer-property?step=terms",
            render(get("/offer-property?step=features")).selectFirst("a[data-prev]")?.attr("href"),
        )
        assertEquals(
            "/offer-property?step=contact",
            render(get("/offer-property?step=review")).selectFirst("a[data-prev]")?.attr("href"),
        )
    }

    @Test
    fun `address step pre-fills the city and neighbourhood`() {
        val page = render(get("/offer-property?step=address"))

        assertEquals("/locations", page.selectFirst("form[data-offer-form]")?.attr("data-locations-url"))
        val city = page.select("select#cityId option").filter { it.attr("value").isNotEmpty() }.single()
        assertEquals("2220957", city.attr("value"))
        assertEquals("Yaoundé", city.text())
        assertTrue(city.hasAttr("selected"))
        val neighborhood = page.select("select#neighborhoodId option").filter { it.attr("value").isNotEmpty() }.single()
        assertEquals("1001", neighborhood.attr("value"))
        assertEquals("Bastos", neighborhood.text())
        assertNull(page.selectFirst("[data-group=neighborhoodId]")?.attr("hidden")?.takeIf { it.isNotEmpty() })
        assertNull(page.selectFirst("input[name=address], input[name=precision], textarea"))
    }

    @Test
    fun `address step without answers shows the placeholders only`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, PendingListing(propertyCategory = PropertyCategory.RESIDENTIAL))

        val page = render(get("/offer-property?step=address"))

        assertEquals(1, page.select("select#cityId option").size)
        assertTrue(page.selectFirst("[data-group=neighborhoodId]")!!.hasAttr("hidden"))
        assertFalse(page.selectFirst("[data-neighborhoods-empty]")!!.hasAttr("hidden"))
    }

    @Test
    fun `terms step shows the tenant currency and date bounds`() {
        val page = render(get("/offer-property?step=terms"))

        val today = FORMATTER.today(clock)
        assertEquals("${FORMATTER.currency()} / mois", page.selectFirst(".input-unit__suffix")?.text())
        val date = page.selectFirst("input[name=availableFrom]")!!
        assertEquals("date", date.attr("type"))
        assertEquals(today.toString(), date.attr("min"))
        assertEquals(today.plusDays(365).toString(), date.attr("max"))
        assertEquals(complete.availableFrom.toString(), date.attr("value"))
        assertEquals("150${' '}000".replace(' ', ' '), page.selectFirst("input[name=priceAmount]")?.attr("value")?.replace(' ', ' '))
        assertEquals("3", page.selectFirst("select[name=advanceRent] option[selected]")?.attr("value"))
        assertEquals("2", page.selectFirst("select[name=securityDeposit] option[selected]")?.attr("value"))
    }

    @Test
    fun `terms step shows the errors`() {
        val page = render(post("/offer-property/terms").param("priceAmount", "10").param("availableFrom", "2020-01-01"))

        val date = page.selectFirst("input[name=availableFrom]")!!
        assertEquals("true", date.attr("aria-invalid"))
        val error = page.selectFirst("#availableFrom-error")!!
        assertFalse(error.hasAttr("hidden"))
        assertTrue(error.selectFirst("[data-error-text]")!!.text().isNotBlank())
        assertEquals("true", page.selectFirst("input[name=priceAmount]")?.attr("aria-invalid"))
        assertTrue(page.select("#securityDeposit-error").first()!!.hasAttr("hidden").not())
    }

    @Test
    fun `features step shows the missing parking answer under its own question`() {
        val page = render(
            post("/offer-property/features")
                .param("propertyType", "APARTMENT").param("furnitureType", "UNFURNISHED").param("rooms", "2"),
        )

        val error = page.selectFirst("#hasParking-error")!!
        assertFalse(error.hasAttr("hidden"))
        assertEquals("Indiquez si le bien a un parking ou un garage.", error.selectFirst("[data-error-text]")!!.text())
        assertEquals("true", page.selectFirst("[data-group=hasParking]")!!.attr("aria-invalid"))
        assertTrue(page.selectFirst("#parkings-error")!!.hasAttr("hidden"))
        assertFalse(page.selectFirst("[data-group=parkings]")!!.hasAttr("aria-invalid"))
    }

    @Test
    fun `features step keeps Oui checked and asks for the places`() {
        val page = render(
            post("/offer-property/features").param("propertyType", "APARTMENT").param("furnitureType", "UNFURNISHED")
                .param("rooms", "2").param("hasParking", "YES"),
        )

        assertTrue(page.selectFirst("input[name=hasParking][value=YES]")!!.hasAttr("checked"))
        assertFalse(page.selectFirst("input[name=hasParking][value=NO]")!!.hasAttr("checked"))
        val error = page.selectFirst("#parkings-error")!!
        assertFalse(error.hasAttr("hidden"))
        assertEquals("Indiquez le nombre de places.", error.selectFirst("[data-error-text]")!!.text())
        assertEquals("true", page.selectFirst("[data-group=parkings]")!!.attr("aria-invalid"))
        assertTrue(page.selectFirst("#hasParking-error")!!.hasAttr("hidden"))
        assertFalse(page.selectFirst("[data-group=hasParking]")!!.hasAttr("aria-invalid"))
    }

    @Test
    fun `features step without JS shows every residential question`() {
        val page = render(get("/offer-property?step=features"))

        assertEquals(
            listOf("APARTMENT", "HOUSE", "ROOM", "STUDIO", "DUPLEX", "BUILDING"),
            page.select("input[name=propertyType]").map { it.attr("value") },
        )
        assertEquals("Villa, maison", page.select("input[name=propertyType][value=HOUSE]").first()!!.parent()!!.text())
        assertTrue(page.select("input[name=propertyType][value=APARTMENT]").first()!!.hasAttr("checked"))
        assertTrue(page.select("input[name=furnitureType][value=UNFURNISHED]").first()!!.hasAttr("checked"))
        assertTrue(page.select("input[name=rooms][value=3]").first()!!.hasAttr("checked"))
        assertTrue(page.select("input[name=hasParking][value=NO]").first()!!.hasAttr("checked"))
        assertEquals(listOf("1", "2", "3+"), page.select("input[name=parkings]").map { it.attr("value") })
        assertNull(page.selectFirst("input[name=area]"))
        assertEquals(listOf("1", "2", "3", "4", "5+"), page.select("input[name=rooms]").map { it.attr("value") })
    }

    @Test
    fun `features step for commercial shows only the area`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            complete.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = "OFFICE", area = 45),
        )

        val page = render(get("/offer-property?step=features"))

        assertEquals(
            listOf("OFFICE", "RETAIL", "INDUSTRIAL", "LAND"),
            page.select("input[name=propertyType]").map { it.attr("value") },
        )
        assertNull(page.selectFirst("input[name=rooms]"))
        assertNull(page.selectFirst("input[name=furnitureType]"))
        assertNull(page.selectFirst("input[name=hasParking]"))
        assertNull(page.selectFirst("input[name=parkings]"))
        assertEquals("45", page.selectFirst("input[name=area]")?.attr("value"))
    }

    @Test
    fun `amenities step pre-checks the saved answers`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            complete.copy(roadAccess = "trackAccessibleByCar", amenities = setOf("waterMeter", "securityGuard")),
        )

        val page = render(get("/offer-property?step=amenities"))

        assertEquals(
            listOf("waterMeter", "securityGuard"),
            page.select("input[name=amenities][checked]").map { it.attr("value") },
        )
        assertEquals(11, page.select("input[name=amenities]").size)
        assertEquals(
            listOf("trackAccessibleByCar"),
            page.select("input[name=roadAccess][checked]").map { it.attr("value") },
        )
        assertEquals(3, page.select("input[name=roadAccess]").size)
    }

    // emptySet() is Kotlin's EmptySet, whose contains(String) SpEL can't call: the first visit has no amenity yet.
    @Test
    fun `amenities step renders without any amenity`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            complete.copy(roadAccess = null, amenities = emptySet()),
        )

        val page = render(get("/offer-property?step=amenities"))

        assertEquals(11, page.select("input[name=amenities]").size)
        assertTrue(page.select("input[name=amenities][checked]").isEmpty())
    }

    @Test
    fun `contact step pre-fills the answers`() {
        val page = render(get("/offer-property?step=contact"))

        assertEquals("Ray", page.selectFirst("input[name=firstName]")?.attr("value"))
        assertEquals("Sponsible", page.selectFirst("input[name=lastName]")?.attr("value"))
        assertEquals("ray@gmail.com", page.selectFirst("input[name=email]")?.attr("value"))
        assertEquals("+237670000001", page.selectFirst("input[name=whatsappNumber]")?.attr("value"))
        assertEquals("MORNING", page.selectFirst("input[name=preferredContactPeriod][checked]")?.attr("value"))
    }

    @Test
    fun `review shows every section and the edit links`() {
        val page = render(get("/offer-property?step=review"))

        assertEquals("Bien", page.select(".review__term").first()?.text())
        assertEquals("Location, usage résidentiel", value(page, "category"))
        assertEquals("Bastos, Yaoundé", value(page, "location"))
        assertTrue(value(page, "terms").contains("par mois"))
        assertTrue(value(page, "terms").contains("Caution de 2 mois"))
        assertTrue(value(page, "property").startsWith("Appartement"))
        assertEquals("Route bitumée, Compteur d'eau", value(page, "amenities"))
        assertTrue(value(page, "contact").contains("ray@gmail.com"))
        // Only the WhatsApp line is formatted by offer-property.js.
        assertEquals(
            listOf("+237670000001"),
            page.select("[data-review-value=contact] [data-phone]").map { it.text() },
        )
        assertEquals(
            listOf("property", "address", "terms", "features", "amenities", "contact")
                .map { "/offer-property?step=$it&returnTo=review" },
            page.select("a.review__edit").map { it.attr("href") },
        )
        assertEquals("Enregistrer mon bien", page.selectFirst("[data-submit]")?.text())
        assertNull(page.selectFirst("[data-submit-error]"))
    }

    @Test
    fun `review shows the submit error`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).create(any())

        val page = render(post("/offer-property/review"))

        val alert = assertNotNull(page.selectFirst("[data-submit-error]"))
        assertTrue(alert.text().contains("Envoi impossible pour le moment"))
    }

    @Test
    fun `confirmation shows the masked e-mail`() {
        val page = confirmation()

        assertEquals("Bien enregistré", page.selectFirst("#confirmation-title")?.text())
        assertEquals("ra•••@gmail.com", page.selectFirst("[data-done-email]")?.text())
        assertEquals("+237670000001", page.selectFirst("[data-done-whatsapp]")?.text())
        assertEquals("le soir", page.selectFirst("[data-done-period]")?.text())
        assertNull(page.selectFirst("[data-progress-block]"))
        assertNull(page.selectFirst("form"))
    }

    private fun confirmation(): Document {
        session.setAttribute(
            OfferPropertyController.SUBMITTED_ATTRIBUTE,
            SubmittedMandate("ray@gmail.com", "+237670000001", ContactPeriodType.EVENING),
        )
        return render(get("/offer-property?step=confirmation"))
    }

    private fun render(request: MockHttpServletRequestBuilder): Document {
        val response = mvc.perform(request.session(session)).andReturn().response
        assertEquals(200, response.status)
        return Jsoup.parse(response.getContentAsString(Charsets.UTF_8))
    }

    private fun value(page: Document, key: String): String =
        page.selectFirst("[data-review-value=$key]")!!.wholeText().trim()
}
