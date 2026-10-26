package com.wutsi.ndopify.npc.ui.listing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.LISTING
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.SENT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AKWA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
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

/** Renders `templates/listing/section.html` through [ListingSectionController] with a real Thymeleaf engine. */
class ListingSectionTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val listingClient = mock<NdListingClient>()
    private val mandateClient = mock<NdMandateClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        ListingFixtures.mockNeighborhoods(locationClient)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(LISTING).whenever(listingClient).get(501)
        doReturn(MANDATE).whenever(mandateClient).get(203)

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
            .standaloneSetup(
                ListingSectionController(currentAgent, listingClient, mandateClient, locationClient, tenantContext, clock),
            )
            .setControllerAdvice(SignedInAdvice())
            .setViewResolvers(viewResolver)
            .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun render(request: MockHttpServletRequestBuilder): Document =
        Jsoup.parse(mvc.perform(request).andReturn().response.contentAsString)

    private fun render(slug: String, listing: Listing? = null): Document {
        listing?.let { doReturn(it).whenever(listingClient).get(501) }
        return render(get("/listings/501/$slug"))
    }

    private fun form(page: Document) = page.selectFirst("form[data-section-form]")!!

    @Test
    fun layout() {
        val page = render("location")

        assertEquals("Emplacement · Annonce · Appartement, Bonapriso — Ndopify Partner Central", page.title())
        assertEquals("listing-section", page.body().attr("data-page"))
        assertNotNull(page.selectFirst("script[src=/assets/js/listing.js]"))
        assertEquals("location", page.body().attr("data-section"))
        assertEquals("Emplacement", page.selectFirst("h1")?.text())
        assertEquals("Annonce · Appartement, Bonapriso · mandat n° 203", page.selectFirst("[data-section-meta]")?.text())
        assertEquals(
            "Vérifiez le quartier, et notez ce qui aidera les locataires à trouver le bien.",
            page.selectFirst("[data-section-instruction]")?.text(),
        )
        assertEquals("/listings/501", page.selectFirst(".back-link a")?.attr("href"))
        assertEquals("← Retour à l'annonce", page.selectFirst(".back-link a")?.text())
        assertNotNull(page.selectFirst("nav.user-menu"))
        assertNull(page.selectFirst("[data-section-readonly]"))

        val form = form(page)
        assertEquals("/listings/501/location", form.attr("action"))
        assertEquals("post", form.attr("method"))
        assertEquals("2", form.selectFirst("input[type=hidden][name=version]")?.attr("value"))
        assertEquals("Enregistrer", form.selectFirst("button[type=submit][data-section-submit]")?.text())
        assertEquals("/listings/501", form.selectFirst(".form__actions a")?.attr("href"))
        assertEquals("Annuler", form.selectFirst(".form__actions a")?.text())
    }

    @Test
    fun location() {
        val form = form(render("location"))

        assertEquals("Douala", form.selectFirst("input#city")?.attr("value"))
        assertTrue(form.selectFirst("input#city")!!.hasAttr("readonly"))
        val select = form.selectFirst("select[name=neighborhoodId]")!!
        assertEquals(listOf("", AKWA.toString(), BONAPRISO.toString()), select.select("option").map { it.attr("value") })
        assertEquals(BONAPRISO.toString(), select.selectFirst("option[selected]")?.attr("value"))
        assertEquals("Derrière l'école publique", form.selectFirst("input[name=landmark]")?.attr("value"))
        assertEquals("100", form.selectFirst("input[name=landmark]")?.attr("maxlength"))
        assertNotNull(form.selectFirst("textarea[name=accessDirections][maxlength=500]"))
    }

    @Test
    fun `location - errors`() {
        val page = render(post("/listings/501/location").param("version", "2").param("landmark", " "))

        val error = page.selectFirst("#neighborhoodId-error")!!
        assertFalse(error.hasAttr("hidden"))
        assertEquals("Choisissez le quartier du bien.", error.text())
        assertEquals("true", page.selectFirst("select[name=neighborhoodId]")?.attr("aria-invalid"))
        assertEquals(
            "Indiquez un point de repère connu : école, carrefour, commerce…",
            page.selectFirst("#landmark-error")?.text(),
        )
        assertTrue(page.selectFirst("#accessDirections-error")?.hasAttr("hidden") ?: true)
    }

    @Test
    fun gps() {
        val form = form(render("gps", LISTING.copy(latitude = 4.0335, longitude = 9.6942)))

        // Without JS, the position is typed: listing.js replaces these with the button
        assertEquals("4.0335", form.selectFirst("input[name=latitude]")?.attr("value"))
        assertEquals("text", form.selectFirst("input[name=latitude]")?.attr("type"))
        assertEquals("9.6942", form.selectFirst("input[name=longitude]")?.attr("value"))
        assertNotNull(form.selectFirst("button[data-gps][hidden]"))
        assertEquals(
            "https://www.openstreetmap.org/?mlat=4.0335&mlon=9.6942#map=18/4.0335/9.6942",
            form.selectFirst("a[data-gps-map]")?.attr("href"),
        )
    }

    @Test
    fun property() {
        val form = form(render("property"))

        assertEquals("RESIDENTIAL", form.selectFirst("input[name=propertyCategory][checked]")?.attr("value"))
        assertEquals("APARTMENT", form.selectFirst("input[name=propertyType][checked]")?.attr("value"))
        assertEquals(11, form.select("input[name=propertyType]").size)
        assertEquals("COMMERCIAL", form.selectFirst("input[name=propertyType][value=LAND]")?.parent()?.attr("data-categories"))
        // Every count question, the script hides those of the type
        listOf("rooms", "livingRooms", "bathrooms", "kitchens", "parkings", "levels", "floorLevel", "area").forEach {
            assertNotNull(form.selectFirst("input[name=$it]"), it)
        }
        assertEquals("rooms", form.selectFirst("input[name=livingRooms]")?.parent()?.attr("data-applies"))
        assertEquals("2", form.selectFirst("input[name=rooms]")?.attr("value"))
        assertEquals("85", form.selectFirst("input[name=area]")?.attr("value"))
        val hint = form.selectFirst("[data-owner-for=rooms]")!!
        assertEquals("Indiqué par le propriétaire : 3", hint.text())
        assertFalse(hint.hasAttr("hidden"))
        assertNull(form.selectFirst("[data-owner-for=area]"))
    }

    @Test
    fun amenities() {
        val form = form(render("amenities"))

        assertEquals("roadPaved", form.selectFirst("input[name=roadAccess][checked]")?.attr("value"))
        assertNotNull(form.selectFirst("input[type=checkbox][name=waterMeter][checked]"))
        assertNotNull(form.selectFirst("input[type=checkbox][name=generatorSet][checked]"))
        assertNull(form.selectFirst("input[type=checkbox][name=solarPanel][checked]"))
        assertEquals("true", form.selectFirst("input[name=solarPanel]")?.attr("value"))
        assertEquals(11, form.select("input[type=checkbox]").size)
        assertEquals("Indiqué par le propriétaire : Compteur d'eau", form.selectFirst("[data-owner-for=amenities]")?.text())
    }

    @Test
    fun `amenities - labels`() {
        val form = form(render("amenities"))

        val groups = form.select("fieldset.choices")
        assertEquals(
            listOf("Compteur d'eau", "Compteur d'eau individuel", "Forage", "Château d'eau, citerne"),
            groups[0].select("label.choice").map { it.text() },
            groups[0].outerHtml(),
        )
        assertEquals(
            listOf("Compteur électrique", "Compteur électrique individuel", "Groupe électrogène", "Panneaux solaires"),
            groups[1].select("label.choice").map { it.text() },
        )
        assertEquals(
            // Jsoup reads the no-break space of "24 h/24" as a space
            listOf("Vidéosurveillance", "Gardien 24 h/24", "Barrière, clôture sécurisée"),
            groups[3].select("label.choice").map { it.text().replace('\u00a0', ' ') },
        )
    }

    @Test
    fun `property - a count's error marks it invalid`() {
        val page = render(
            post("/listings/501/property")
                .param("version", "2")
                .param("propertyCategory", "RESIDENTIAL")
                .param("propertyType", "APARTMENT")
                .param("furnitureType", "UNFURNISHED")
                .param("rooms", "abc")
                .param("bathrooms", "1")
                .param("area", "85"),
        )

        assertEquals("Indiquez un nombre entier.", page.selectFirst("#rooms-error")?.text())
        assertEquals("true", page.selectFirst("input[name=rooms]")?.attr("aria-invalid"))
        assertFalse(page.selectFirst("input[name=area]")!!.hasAttr("aria-invalid"))
    }

    @Test
    fun terms() {
        val form = form(render("terms"))

        assertEquals("150 000", form.selectFirst("input[name=price]")?.attr("value"))
        assertEquals("FCFA / mois", form.selectFirst("input[name=price] + .input-unit__suffix")?.text())
        assertEquals("3", form.selectFirst("select[name=securityDeposit] option[selected]")?.attr("value"))
        assertEquals((1..6).map { "$it" }, form.select("select[name=securityDeposit] option").map { it.attr("value") }.drop(1))
        assertEquals((1..12).map { "$it" }, form.select("select[name=advanceRent] option").map { it.attr("value") }.drop(1))
        assertEquals(listOf("6", "12", "24", "36"), form.select("select[name=leaseTerm] option").map { it.attr("value") }.drop(1))
        assertEquals(listOf("1", "2", "3"), form.select("select[name=noticePeriod] option").map { it.attr("value") }.drop(1))
        val date = form.selectFirst("input[name=availableFrom]")!!
        assertEquals("date", date.attr("type"))
        assertEquals("2027-10-26", date.attr("max"))
        assertEquals("Indiqué par le propriétaire : 2 mois", form.selectFirst("[data-owner-for=securityDeposit]")?.text())
    }

    @Test
    fun remarks() {
        val form = form(render("remarks", LISTING.copy(agentRemarks = "Peinture à refaire")))

        val remarks = form.selectFirst("textarea[name=agentRemarks]")!!
        assertEquals("Peinture à refaire", remarks.text())
        assertEquals("1000", remarks.attr("maxlength"))
    }

    @Test
    fun `read-only once sent`() {
        val page = render("amenities", LISTING.copy(status = ListingStatus.UNDER_VERIFICATION, modifiedAt = SENT))

        assertEquals(
            "Annonce en vérification depuis le 22 oct. 2026 : voici ce que vous avez relevé, il ne peut plus être modifié.",
            page.selectFirst("[data-section-readonly]")?.text(),
        )
        assertNull(page.selectFirst("[data-section-instruction]"))
        val form = form(page)
        assertTrue(form.hasClass("listing-readonly"))
        assertNull(form.selectFirst("[data-section-submit]"))
        assertTrue(form.select("input:not([type=hidden])").all { it.hasAttr("disabled") })
        // The groups without an answer are left out
        assertNull(form.selectFirst("input[name=solarPanel]"))
        assertNotNull(form.selectFirst("input[name=generatorSet]"))
        // As the prototype's showListingReadonly: what the landlord said stays visible
        assertEquals("Indiqué par le propriétaire : Compteur d'eau", form.selectFirst("[data-owner-for=amenities]")?.text())
        val back = form.selectFirst(".form__actions a")!!
        assertEquals("Retour à l'annonce", back.text())
        assertEquals("btn btn--primary", back.className())
    }

    @Test
    fun `read-only - fields without an answer are left out`() {
        val form = form(render("property", LISTING.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT)))

        assertNotNull(form.selectFirst("input[name=rooms][disabled]"))
        assertNull(form.selectFirst("input[name=kitchens]"))
        assertNull(form.selectFirst("input[name=furnitureType]"))
        assertNull(form.selectFirst("input[name=propertyType][value=VILLA]"))
    }

    @Test
    fun `save error`() {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = ErrorCode.LISTING_VERSION_CONFLICT)) }
        doThrow(ex).whenever(listingClient).update(any(), any())
        doReturn(LISTING).doReturn(LISTING.copy(version = 3)).whenever(listingClient).get(501)

        val page = render(
            post("/listings/501/gps").param("version", "2").param("latitude", "4.0335").param("longitude", "9.6942"),
        )

        val alert = page.selectFirst("[data-section-error]")!!
        assertEquals("alert", alert.attr("role"))
        assertTrue(alert.text().startsWith("Cette annonce a été modifiée entre-temps."))
        assertEquals("3", page.selectFirst("input[name=version]")?.attr("value"))
        assertEquals("4.0335", page.selectFirst("input[name=latitude]")?.attr("value"))
    }

    @Test
    fun locked() {
        doReturn(LISTING.copy(agentId = 999)).whenever(listingClient).get(501)

        val page = render(get("/listings/501/gps"))

        assertEquals("Cette annonce n'est pas disponible", page.selectFirst("[data-section-locked] h1")?.text())
        assertEquals("/#mandates-title", page.selectFirst(".back-link a")?.attr("href"))
        assertNull(page.selectFirst("form[data-section-form]"))
    }

    @Test
    fun unavailable() {
        doThrow(HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)).whenever(listingClient).get(501)

        val page = render(get("/listings/501/gps"))

        assertEquals("Annonce indisponible pour le moment", page.selectFirst("[data-section-unavailable] h1")?.text())
        assertNull(page.selectFirst("[data-section-locked]"))
    }
}
