package com.wutsi.ndopify.npc.ui.findproperty

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
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import com.wutsi.ndopify.refdata.dto.Tenant
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Renders `templates/find-property.html` through [FindPropertyController] with a real Thymeleaf engine, to catch
 * template mistakes (expressions, conditions, pre-filled values) that [FindPropertyControllerTest] can't see.
 */
class FindPropertyTemplateTest {
    private val mandateClient = mock<NdMandateClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var session: MockHttpSession
    private lateinit var mvc: MockMvc

    private val locations = listOf(
        Location(id = 2220957, name = "Yaoundé", type = LocationType.CITY, country = "CM"),
        Location(id = 1001, parentId = 2220957, name = "Bastos", type = LocationType.NEIGHBORHOOD, country = "CM"),
        Location(id = 1002, parentId = 2220957, name = "Essos", type = LocationType.NEIGHBORHOOD, country = "CM"),
    )

    private val complete = PendingMandateTest.COMPLETE

    private val tenant = Tenant(
        id = 1,
        name = "Ndopify Cameroun",
        countryCode = "CM",
        currencyCode = "XAF",
        logoUrl = "https://cdn.ndopify.com/tenant/1/logo.png",
        publicPortalUrl = "https://www.ndopify.com",
    )

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
        mvc = MockMvcBuilders.standaloneSetup(FindPropertyController(mandateClient, locationClient, tenantContext))
            .setViewResolvers(viewResolver)
            .build()
        session = MockHttpSession()
    }

    @Test
    fun `header shows the tenant's logo and a way back to its public portal`() {
        val page = render(get("/find-property"))

        val logo = page.selectFirst(".site-header__brand img")!!
        assertEquals(tenant.logoUrl, logo.attr("src"))
        assertEquals(tenant.name, logo.attr("alt"))
        assertEquals(tenant.publicPortalUrl, page.selectFirst(".site-header__brand")?.attr("href"))
        val exit = assertNotNull(page.selectFirst("[data-exit]"))
        assertEquals("Quitter", exit.text())
        assertEquals(tenant.publicPortalUrl, exit.attr("href"))
    }

    @Test
    fun `header falls back to Ndopify for a tenant without branding`() {
        doReturn(Tenant(id = 1, countryCode = "CM")).whenever(tenantContext).getTenant()

        val page = render(get("/find-property"))

        assertEquals("/assets/images/logo.png", page.selectFirst(".site-header__brand img")?.attr("src"))
        assertEquals("/find-property", page.selectFirst(".site-header__brand")?.attr("href"))
        assertNull(page.selectFirst("[data-exit]"))
    }

    @Test
    fun need() {
        val page = render(get("/find-property"))

        assertEquals("Trouver un bien — Ndopify", page.title())
        assertEquals("Étape 1 sur 7", page.selectFirst("[data-progress-label]")?.text())
        assertEquals("/find-property/need", form(page).attr("action"))
        assertEquals(listOf("need"), page.select("[data-step]").map { it.attr("data-step") })
        assertNull(page.selectFirst("[data-prev]")) // the first step has nowhere to go back to
        assertEquals("Suivant", page.selectFirst("[data-next]")?.text())
        assertNull(page.selectFirst("input[name=returnTo]"))
        assertTrue(page.select(".field__error").all { it.hasAttr("hidden") })
    }

    @Test
    fun needPrefilled() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property"))

        assertTrue(page.selectFirst("input[name=propertyCategory][value=RESIDENTIAL]")!!.hasAttr("checked"))
        assertFalse(page.selectFirst("input[name=propertyCategory][value=COMMERCIAL]")!!.hasAttr("checked"))
    }

    @Test
    fun needError() {
        val page = render(post("/find-property/need"))

        val error = page.selectFirst("#propertyCategory-error")!!
        assertFalse(error.hasAttr("hidden"))
        assertEquals("Indiquez si le bien est pour y habiter ou pour une activité.", error.text())
        assertEquals("true", page.selectFirst("[data-group=propertyCategory]")?.attr("aria-invalid"))
    }

    @Test
    fun areaShowsChosenCity() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property?step=area"))

        assertEquals("/find-property/area", form(page).attr("action"))
        assertEquals("/locations", form(page).attr("data-locations-url"))
        assertEquals("Yaoundé", page.selectFirst("#cityId option[selected]")?.text())
        assertEquals(listOf("Bastos", "Essos"), page.select("#neighborhoodIds option[selected]").map { it.text() })
        assertEquals("2 sur 3 sélectionnés", page.selectFirst("[data-neighborhood-counter]")?.text())
        assertEquals("/find-property", page.selectFirst("[data-prev]")?.attr("href"))
    }

    @Test
    fun propertyShowsOnlyCategoryTypes() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = null, rooms = null),
        )

        val page = render(get("/find-property?step=property"))

        val types = page.select("input[name=propertyType]").map { it.attr("value") }
        assertEquals(listOf("RETAIL", "OFFICE", "INDUSTRIAL", "BUILDING", "LAND", "ANY"), types)
        assertNull(page.selectFirst("[data-group=rooms]"))
        assertNotNull(page.selectFirst("[data-group=furnitureType]"))
        assertNotNull(page.selectFirst("[data-group=parkings]"))
        assertEquals("Bureau", page.selectFirst("input[value=OFFICE]")!!.parent()!!.text())
    }

    @Test
    fun propertyPrefilled() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(area = 1200))

        val page = render(get("/find-property?step=property"))

        assertTrue(page.selectFirst("input[name=propertyType][value=APARTMENT]")!!.hasAttr("checked"))
        assertTrue(page.selectFirst("input[name=rooms][value=3]")!!.hasAttr("checked"))
        assertTrue(page.selectFirst("input[name=furnitureType][value=ANY]")!!.hasAttr("checked"))
        assertTrue(page.selectFirst("input[name=parkings][value=1]")!!.hasAttr("checked"))
        assertEquals("1 200", page.selectFirst("#area")?.attr("value"))
        // Without JS every follow-up question is visible; find-property.js hides those that don't apply.
        assertFalse(page.selectFirst("[data-group=rooms]")!!.hasAttr("hidden"))
    }

    @Test
    fun amenitiesPrefilled() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property?step=amenities"))

        assertEquals(14, page.select("input[name=amenities]").size)
        assertTrue(page.selectFirst("input[name=amenities][value=waterMeter]")!!.hasAttr("checked"))
        assertFalse(page.selectFirst("input[name=amenities][value=solarPanel]")!!.hasAttr("checked"))
        assertEquals("Compteur d'eau", page.selectFirst("input[value=waterMeter]")!!.parent()!!.text())
    }

    @Test
    fun budgetFormatted() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property?step=budget"))

        assertEquals("150 000", page.selectFirst("#priceAmount")?.attr("value"))
        assertTrue(page.selectFirst("input[name=movingPeriod][value=WITHIN_1_MONTH]")!!.hasAttr("checked"))
        assertEquals("Date d'emménagement souhaitée", page.selectFirst("[data-move-in-label]")?.text())
    }

    @Test
    fun budgetForCommercial() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = "OFFICE", rooms = null),
        )

        val page = render(get("/find-property?step=budget"))

        assertEquals("Date d'installation souhaitée", page.selectFirst("[data-move-in-label]")?.text())
    }

    @Test
    fun budgetError() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(post("/find-property/budget").param("priceAmount", "100"))

        assertEquals("100", page.selectFirst("#priceAmount")?.attr("value"))
        assertEquals(
            "Indiquez un loyer d'au moins 5K FCFA par mois.",
            page.selectFirst("#priceAmount-error")?.text()
        )
        assertEquals("true", page.selectFirst("#priceAmount")?.attr("aria-invalid"))
    }

    @Test
    fun contactPrefilledAndEscaped() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(comments = "<script>alert(1)</script>"))

        val page = render(get("/find-property?step=contact"))

        assertEquals("Ray", page.selectFirst("#firstName")?.attr("value"))
        assertEquals("+237670000001", page.selectFirst("#whatsappNumber")?.attr("value"))
        assertTrue(page.selectFirst("input[name=preferredContactPeriod][value=EVENING]")!!.hasAttr("checked"))
        assertEquals("<script>alert(1)</script>", page.selectFirst("#comments")?.text())
        assertNull(page.selectFirst("#comments script"))
    }

    @Test
    fun returningShowsBackToReview() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property?step=budget&returnTo=review"))

        assertEquals("Revenir à la revue", page.selectFirst("[data-next]")?.text())
        assertEquals("review", page.selectFirst("input[name=returnTo]")?.attr("value"))
        assertEquals("/find-property?step=review", page.selectFirst("[data-prev]")?.attr("href"))
    }

    @Test
    fun reviewRendersLabels() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property?step=review"))

        assertEquals("/find-property/review", form(page).attr("action"))
        assertEquals("Location, usage résidentiel", value(page, "project"))
        assertEquals("Yaoundé — Bastos, Essos", value(page, "sector"))
        assertEquals(
            "Appartement · 3 chambres · Meublé ou non · 1 place de parking · 80 m² minimum",
            value(page, "property"),
        )
        assertEquals("Compteur d'eau, Gardien", value(page, "amenities"))
        assertEquals("150K FCFA par mois", value(page, "price"))
        assertEquals("Dans moins d'1 mois", value(page, "movingPeriod"))
        assertTrue(value(page, "contact").contains("Ray Sponsible"))
        assertTrue(value(page, "contact").contains("ray@gmail.com"))
        assertTrue(value(page, "contact").contains("Contact le soir"))
        assertEquals("Près d'une école", value(page, "comments"))
        assertNull(page.selectFirst("[data-submit-error]"))
    }

    @Test
    fun reviewWithoutOptionalAnswers() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(amenities = emptySet(), comments = ""))

        val page = render(get("/find-property?step=review"))

        assertEquals("Aucun critère", value(page, "amenities"))
        assertEquals("Aucune", value(page, "comments"))
    }

    @Test
    fun reviewEditLinks() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property?step=review"))

        val links = page.select("a.review__edit").map { it.attr("href") }.distinct()
        assertEquals(
            listOf("need", "area", "property", "amenities", "budget", "contact")
                .map { "/find-property?step=$it&returnTo=review" },
            links,
        )
    }

    @Test
    fun reviewServerError() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).create(any())

        val page = render(post("/find-property/review"))

        val alert = assertNotNull(page.selectFirst("[data-submit-error]"))
        assertTrue(alert.text().contains("Envoi impossible pour le moment"))
    }

    @Test
    fun reviewServerDown() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)
        doThrow(ResourceAccessException("Connection refused")).whenever(mandateClient).create(any())
        doThrow(ResourceAccessException("Connection refused")).whenever(locationClient).search(any())

        val page = render(post("/find-property/review"))

        assertTrue(page.selectFirst("[data-submit-error]")!!.text().contains("Envoi impossible pour le moment"))
        assertEquals("Secteur choisi", value(page, "sector"))
    }

    @Test
    fun confirmation() {
        session.setAttribute(
            SubmittedMandate.SESSION_ATTRIBUTE,
            SubmittedMandate("ray@gmail.com", "+237670000001", ContactPeriodType.EVENING),
        )

        val page = render(get("/find-property?step=confirmation"))

        assertEquals("Demande envoyée — Ndopify", page.title())
        assertEquals("Demande envoyée", page.selectFirst("#confirmation-title")?.text())
        assertEquals("ra•••@gmail.com", page.selectFirst("[data-done-email]")?.text())
        assertEquals("+237670000001", page.selectFirst("[data-done-whatsapp]")?.text())
        assertEquals("le soir", page.selectFirst("[data-done-period]")?.text())
        assertNull(page.selectFirst("[data-progress-block]"))
        assertNull(page.selectFirst("form"))
        assertEquals(tenant.publicPortalUrl, page.selectFirst(".cta-row a")?.attr("href"))
    }

    private fun render(request: MockHttpServletRequestBuilder): Document {
        val response = mvc.perform(request.session(session)).andReturn().response
        assertEquals(200, response.status)
        return Jsoup.parse(response.getContentAsString(Charsets.UTF_8))
    }

    private fun form(page: Document) = page.select("form[data-find-form]").single()

    private fun value(page: Document, key: String): String =
        page.selectFirst("[data-review-value=$key]")!!.wholeText().trim()
}
