package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.config.Parallel
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.CONTRACT
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NOW
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.ACCEPTED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.APPROVED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.CUSTOMER
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.PENDING
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AKWA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.web.core.service.TenantContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/mandate/edit.html` through [MandateController] with a real Thymeleaf engine. */
class MandateEditTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val contractClient = mock<NdContractClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val assignmentClient = mock<NdAssignmentClient>()
    private val listingClient = mock<NdListingClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(NOW).whenever(clock).millis()
        doReturn(PENDING).whenever(mandateClient).get(201)
        doReturn(CONTRACT).whenever(contractClient).get("c-201")
        doReturn(CUSTOMER).whenever(customerClient).get(555)
        doReturn(ACCEPTED).whenever(assignmentClient).accepted(201)

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
                MandateController(
                    currentAgent,
                    mandateClient,
                    contractClient,
                    customerClient,
                    assignmentClient,
                    listingClient,
                    mock(),
                    mock(),
                    mock(),
                    locationClient,
                    tenantContext,
                    clock,
                    MandateDealCandidates(mock(), mandateClient, customerClient, listingClient),
                    Parallel(),
                ),
            )
            .setControllerAdvice(SignedInAdvice())
            .setViewResolvers(viewResolver)
            .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun render(request: AbstractMockHttpServletRequestBuilder<*>): Document =
        Jsoup.parse(mvc.perform(request).andReturn().response.contentAsString)

    private fun render(mandate: Mandate): Document {
        doReturn(mandate).whenever(mandateClient).get(201)
        return render(get("/mandates/201/edit"))
    }

    @Test
    fun form() {
        val page = render(MANDATE.copy(waterWell = true, version = 3))

        assertEquals("Modifier le mandat 201 — Ndopify Partner Central", page.title())
        assertEquals("Modifier le mandat", page.selectFirst("h1")?.text())
        assertEquals("Locataire · Maison · mandat n° 201", page.selectFirst(".mandate__meta")?.text())
        assertEquals("/mandates/201", page.selectFirst(".back-link a")?.attr("href"))
        assertNotNull(page.selectFirst("nav.user-menu"))
        assertNull(page.selectFirst("[data-edit-resend]"))

        val form = page.selectFirst("form[data-edit-form]")!!
        assertEquals("/mandates/201/edit", form.attr("action"))
        assertEquals("3", form.selectFirst("input[type=hidden][name=version]")?.attr("value"))
        assertEquals("post", form.attr("method"))
        assertEquals(
            listOf("sector", "property", "amenities", "budget", "comments"),
            form.select("details.edit-group").map { it.attr("data-edit-group") },
        )
        assertTrue(form.select("details.edit-group").none { it.hasAttr("open") })

        // Sector: the city is read-only; the chosen neighbourhoods, for slim-select to enhance.
        assertEquals("Douala", form.selectFirst(".edit-readonly")?.text())
        assertNull(form.selectFirst("[name=cityId]"))
        val select = form.selectFirst("select[name=neighborhoodIds]")!!
        assertTrue(select.hasAttr("multiple"))
        assertEquals(DOUALA.toString(), select.attr("data-city-id"))
        assertEquals(listOf(BONAPRISO.toString(), AKWA.toString()), select.select("option[selected]").map { it.attr("value") })
        assertEquals("Bonapriso, Akwa", form.selectFirst("[data-edit-group=sector] [data-group-value]")?.text())

        // Property: every follow-up question without JS, the types tagged with their categories.
        assertTrue(form.selectFirst("input[name=propertyCategory][value=RESIDENTIAL]")!!.hasAttr("checked"))
        assertTrue(form.selectFirst("input[name=propertyType][value=HOUSE]")!!.hasAttr("checked"))
        assertNotNull(form.selectFirst("input[name=propertyType][value=ANY]"))
        val typeGrid = form.selectFirst("[data-group=propertyType] > .choices__grid")!!
        assertFalse(typeGrid.hasClass("choices__grid--2"))
        assertTrue(form.selectFirst("[data-group=rooms] > .choices__grid")!!.hasClass("choices__grid--2"))
        assertTrue(form.selectFirst("[data-group=parkings] > .choices__grid")!!.hasClass("choices__grid--2"))
        assertTrue(form.selectFirst("input[name=rooms][value=3]")!!.hasAttr("checked"))
        assertTrue(form.selectFirst("input[name=furnitureType][value=ANY]")!!.hasAttr("checked"))
        assertTrue(form.selectFirst("input[name=parkings][value=ANY]")!!.hasAttr("checked"))
        assertNotNull(form.selectFirst("input[name=area]"))
        assertEquals("Surface minimale", form.selectFirst("label[for=area] span")?.text())

        assertTrue(form.selectFirst("input[name=amenities][value=waterWell]")!!.hasAttr("checked"))
        assertFalse(form.selectFirst("input[name=amenities][value=waterMeter]")!!.hasAttr("checked"))
        assertEquals(14, form.select("input[name=amenities]").size)

        assertEquals("Budget et emménagement", form.selectFirst("[data-edit-group=budget] .edit-group__title")?.text())
        assertEquals("Budget mensuel", form.selectFirst("label[for=priceAmount]")?.text())
        assertEquals("FCFA / mois", form.selectFirst("input[name=priceAmount] + .input-unit__suffix")?.text())
        assertEquals("250 000", form.selectFirst("input[name=priceAmount]")?.attr("value")?.replace('\u00a0', ' '))
        assertTrue(form.selectFirst("input[name=movingPeriod][value=WITHIN_3_MONTHS]")!!.hasAttr("checked"))
        assertEquals("Près d'une école", form.selectFirst("textarea[name=comments]")?.text())
        assertEquals("/mandates/201", form.selectFirst(".form__actions a")?.attr("href"))
        assertEquals("Enregistrer", form.selectFirst(".form__actions button[type=submit]")?.text())
    }

    @Test
    fun `a rental no longer offers land`() {
        val page = render(MANDATE)
        assertNull(page.selectFirst("input[name=propertyType][value=LAND]"))
    }

    @Test
    fun `a stored rental land stays checked and readable`() {
        val page = render(MANDATE.copy(propertyCategory = PropertyCategory.RESIDENTIAL, propertyType = PropertyType.LAND))
        val form = page.selectFirst("form[data-edit-form]")!!
        assertEquals("false", form.attr("data-sale"))
        val land = assertNotNull(page.selectFirst("input[name=propertyType][value=LAND]"))
        assertTrue(land.hasAttr("checked"))
        // #304: the types come from NdUtil.propertyRules, not from per-radio attributes.
        assertFalse(land.parent()!!.hasAttr("data-categories"))
        assertFalse(land.parent()!!.hasAttr("data-rental-categories"))
    }

    @Test
    fun `a sale offers land under both usages`() {
        val page = render(
            MANDATE.copy(listingType = ListingType.SALE, propertyCategory = PropertyCategory.RESIDENTIAL),
        )
        val form = page.selectFirst("form[data-edit-form]")!!
        assertEquals("true", form.attr("data-sale"))
        val land = assertNotNull(page.selectFirst("input[name=propertyType][value=LAND]"))
        assertFalse(land.hasAttr("checked"))
        assertFalse(land.parent()!!.hasAttr("data-categories"))
        // One radio, never one per usage
        assertEquals(1, page.select("input[name=propertyType][value=LAND]").size)
        // "Indifférent" is the client's answer only: the script hides it on a landlord's page.
        assertTrue(page.selectFirst("input[name=propertyType][value=ANY]")!!.parent()!!.hasAttr("data-tenant-only"))
    }

    @Test
    fun `a sale's edit page shows its documents and its rate`() {
        val page = render(
            MANDATE.copy(
                mandateType = MandateType.LISTING,
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = PropertyType.LAND,
                rooms = null,
                furnitureType = null,
                parkings = null,
                area = 300,
                price = Money(45_000_000, "XAF", PeriodicityType.NONE),
                ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE),
            ),
        )

        assertNotNull(page.selectFirst("[data-group=documents]:not([hidden])"))
        assertNotNull(page.selectFirst("input[name=documents][value=LAND_TITLE][checked]"))
        assertNull(page.selectFirst("input[name=documents][value=SURVEY_PLAN][checked]"))
        assertTrue(page.select("[data-rate]").text().contains("FCFA/m²"))
    }

    @Test
    fun `a search's edit page shows the documents it requires`() {
        val page = render(
            MANDATE.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = PropertyType.HOUSE,
                price = Money(15_000_000, "XAF", PeriodicityType.NONE),
                titleDocuments = listOf(TitleDocumentType.SURVEY_PLAN),
            ),
        )
        val form = page.selectFirst("form[data-edit-form]")!!

        assertNotNull(page.selectFirst("[data-group=documents]:not([hidden])"))
        assertEquals("Documents exigés", form.selectFirst("[data-group=documents] legend")?.text()?.substringBefore(" (facultatif)"))
        assertNotNull(page.selectFirst("input[name=documents][value=SURVEY_PLAN][checked]"))
        // A house is not sold by its surface: no rate line.
        assertTrue(page.select("[data-rate]").text().isEmpty())
    }

    @Test
    fun `a rental's edit page has no documents and a grouped surface`() {
        val page = render(MANDATE.copy(area = 150))
        val form = page.selectFirst("form[data-edit-form]")!!

        assertTrue(form.selectFirst("[data-group=documents]")!!.hasAttr("hidden"))
        assertEquals("150", form.selectFirst("input[name=area]")?.`val`())
        assertTrue(form.selectFirst("[data-group=area]")!!.hasAttr("hidden"))
    }

    @Test
    fun `a stored rental building keeps its type`() {
        val page = render(
            MANDATE.copy(
                listingType = ListingType.RENTAL,
                propertyCategory = PropertyCategory.COMMERCIAL,
                propertyType = PropertyType.BUILDING,
                area = 300,
                furnitureType = FurnitureType.UNFURNISHED,
            ),
        )
        val form = page.selectFirst("form[data-edit-form]")!!
        val building = page.selectFirst("input[name=propertyType][value=BUILDING]")!!

        assertTrue(building.hasAttr("checked"))
        assertFalse(building.hasAttr("disabled"))
        // The questions a rental building had, while it stays the saved answer.
        assertFalse(form.selectFirst("[data-group=furnitureType]")!!.hasAttr("hidden"))
        assertFalse(form.selectFirst("[data-group=area]")!!.hasAttr("hidden"))
        assertTrue(form.selectFirst("[data-group=rooms]")!!.hasAttr("hidden"))
        assertTrue(form.selectFirst("[data-group=parkings]")!!.hasAttr("hidden"))
        assertTrue(form.selectFirst("[data-group=levels]")!!.hasAttr("hidden"))
    }

    @Test
    fun `pending approval - notice`() {
        val page = render(PENDING)

        assertEquals(
            "Ce mandat attend la signature du client : en l'enregistrant, vous annulez le contrat qui lui a été envoyé. " +
                "Il ne pourra plus le signer, et vous devrez lui envoyer la nouvelle version.",
            page.selectFirst("[data-edit-resend]")?.text()?.replace('\u00a0', ' '),
        )
    }

    @Test
    fun landlord() {
        val page = render(MANDATE.copy(mandateType = MandateType.LISTING))

        val form = page.selectFirst("form[data-edit-form]")!!
        assertTrue(form.select("input[value=ANY]").isEmpty())
        assertEquals("Surface", form.selectFirst("label[for=area] span")?.text())
        assertEquals("Loyer et disponibilité", form.selectFirst("[data-edit-group=budget] .edit-group__title")?.text())
        assertEquals("Loyer mensuel", form.selectFirst("label[for=priceAmount]")?.text())
        assertEquals("Disponibilité du bien", form.selectFirst("fieldset[data-group=movingPeriod] legend")?.text())
        assertEquals("Ce dont le bien dispose.", form.selectFirst("[data-edit-group=amenities] .edit-group__body > p")?.text())
    }

    @Test
    fun errors() {
        val page = render(
            post("/mandates/201/edit")
                .param("propertyCategory", "RESIDENTIAL")
                .param("propertyType", "APARTMENT")
                .param("furnitureType", "ANY")
                .param("priceAmount", "abc")
                .param("movingPeriod", "WITHIN_1_MONTH"),
        )

        val form = page.selectFirst("form[data-edit-form]")!!
        assertTrue(form.selectFirst("[data-edit-group=property]")!!.hasAttr("open"))
        assertTrue(form.selectFirst("[data-edit-group=budget]")!!.hasAttr("open"))
        assertFalse(form.selectFirst("[data-edit-group=sector]")!!.hasAttr("open"))
        assertEquals("Indiquez le nombre de chambres.", form.selectFirst("#rooms-error")?.text())
        assertFalse(form.selectFirst("#rooms-error")!!.hasAttr("hidden"))
        assertEquals("abc", form.selectFirst("input[name=priceAmount]")?.attr("value"))
        assertEquals("true", form.selectFirst("input[name=priceAmount]")?.attr("aria-invalid"))
        assertTrue(form.selectFirst("#area-error")!!.hasAttr("hidden"))
    }

    @Test
    fun `save error`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).update(any(), any())

        val page = render(
            post("/mandates/201/edit")
                .param("propertyCategory", "RESIDENTIAL")
                .param("propertyType", "HOUSE")
                .param("rooms", "3")
                .param("furnitureType", "ANY")
                .param("priceAmount", "150000")
                .param("movingPeriod", "WITHIN_1_MONTH"),
        )

        assertEquals(
            "Enregistrement impossible pour le moment. Réessayez dans quelques instants.",
            page.selectFirst("[data-edit-error]")?.text(),
        )
    }

    @Test
    fun `a sale search's edit page reads budget and purchase`() {
        val page = render(
            MandateCardsTest.MANDATE.copy(
                listingType = ListingType.SALE,
                price = Money(15_000_000, "XAF", PeriodicityType.NONE),
            ),
        )

        val form = page.selectFirst("form[data-edit-form]")!!
        assertEquals("Budget et achat", form.selectFirst("[data-edit-group=budget] .edit-group__title")?.text())
        assertEquals("Budget", form.selectFirst("label[for=priceAmount]")?.text())
        assertEquals("FCFA", form.selectFirst("input[name=priceAmount] + .input-unit__suffix")?.text())
        assertEquals("Quand souhaitez-vous acheter ?", form.selectFirst("fieldset[data-group=movingPeriod] legend")?.text())
        // #304: a residential sale asks its furniture too.
        assertFalse(form.selectFirst("[data-group=furnitureType]")!!.hasAttr("hidden"))
        assertFalse(page.text().contains("Budget mensuel"))
        assertFalse(page.text().contains("/ mois"))

        val budget = form.selectFirst("[data-edit-group=budget] [data-group-value]")?.text()
            ?.replace('\u00a0', ' ')
            ?.replace('\u202f', ' ')
        assertEquals("15M FCFA · Dans 1 à 3 mois", budget)
    }

    @Test
    fun `a sale listing's edit page reads price and availability`() {
        val page = render(
            MandateCardsTest.MANDATE.copy(
                mandateType = MandateType.LISTING,
                listingType = ListingType.SALE,
                price = Money(45_000_000, "XAF", PeriodicityType.NONE),
            ),
        )

        val form = page.selectFirst("form[data-edit-form]")!!
        assertEquals("Prix et disponibilité", form.selectFirst("[data-edit-group=budget] .edit-group__title")?.text())
        assertEquals("Prix", form.selectFirst("label[for=priceAmount]")?.text())
        assertEquals("Disponibilité du bien", form.selectFirst("fieldset[data-group=movingPeriod] legend")?.text())
        assertFalse(form.selectFirst("[data-group=furnitureType]")!!.hasAttr("hidden"))
        assertFalse(page.text().contains("/ mois"))
    }

    @Test
    fun `a sale's price error names the budget`() {
        doReturn(MandateCardsTest.MANDATE.copy(listingType = ListingType.SALE)).whenever(mandateClient).get(201)

        val page = render(
            post("/mandates/201/edit")
                .param("propertyCategory", "RESIDENTIAL")
                .param("propertyType", "HOUSE")
                .param("rooms", "3")
                .param("movingPeriod", "WITHIN_1_MONTH"),
        )

        assertEquals("Indiquez le budget, en FCFA.", page.selectFirst("#priceAmount-error")?.text()?.replace('\u00a0', ' '))
    }

    @Test
    fun `a building sale's edit page asks its étages and its unités`() {
        val page = render(
            MandateCardsTest.MANDATE.copy(
                mandateType = MandateType.LISTING,
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.COMMERCIAL,
                propertyType = PropertyType.BUILDING,
                rooms = null,
                furnitureType = null,
                parkings = null,
                levels = 3,
                units = 12,
                area = 850,
                price = Money(250_000_000, "XAF", PeriodicityType.NONE),
            ),
        )

        val form = page.selectFirst("form[data-edit-form]")!!
        val levels = assertNotNull(form.selectFirst("input[name=levels]"))
        assertEquals("3", levels.attr("value"))
        assertEquals("Étages (1 à 30)", form.selectFirst("label[for=levels]")?.text())
        assertFalse(form.selectFirst("[data-group=levels] label")!!.text().contains("facultatif"))
        val units = assertNotNull(form.selectFirst("input[name=units]"))
        assertEquals("12", units.attr("value"))
        assertEquals("Unités (1 à 500)", form.selectFirst("label[for=units]")?.text())
        assertFalse(form.selectFirst("[data-group=units] label")!!.text().contains("facultatif"))

        assertNull(form.selectFirst("[data-group=rooms]"))
        assertNull(form.selectFirst("[data-group=parkings]"))
        assertNull(form.selectFirst("[data-edit-group=amenities]"))
        assertEquals(
            listOf("sector", "property", "budget", "comments"),
            form.select("details.edit-group").map { it.attr("data-edit-group") },
        )
        val summary = form.selectFirst("[data-edit-group=property] [data-group-value]")?.text()
            ?.replace('\u00a0', ' ')
            ?.replace('\u202f', ' ')
        assertEquals("Immeuble · 3 étages · 12 unités · 850 m²", summary)
        assertEquals(
            "Soit ≈ 20,83M FCFA/unité pour 12 unités",
            form.selectFirst("[data-rate]")?.text(),
        )
    }

    @Test
    fun `a non-building edit page renders the counts hidden`() {
        val form = render(MANDATE).selectFirst("form[data-edit-form]")!!

        // In the DOM (hidden), so that a type change to BUILDING reveals them without a round trip.
        assertNotNull(form.selectFirst("input[name=levels]"))
        assertNotNull(form.selectFirst("input[name=units]"))
        assertTrue(form.selectFirst("[data-group=levels]")!!.hasAttr("hidden"))
        assertTrue(form.selectFirst("[data-group=units]")!!.hasAttr("hidden"))
    }

    @Test
    fun `locked - approved`() {
        val page = render(APPROVED)

        assertEquals("Ce mandat ne peut plus être modifié", page.selectFirst("[data-edit-locked] h1")?.text())
        assertEquals("Un mandat approuvé par le client ne se modifie plus.", page.selectFirst("[data-edit-locked] p")?.text())
        assertNull(page.selectFirst("form[data-edit-form]"))
        assertEquals("/mandates/201", page.selectFirst(".back-link a")?.attr("href"))
    }

    @Test
    fun `locked - unavailable`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val page = render(get("/mandates/201/edit"))

        assertEquals("Ce mandat n'est pas disponible", page.selectFirst("[data-edit-locked] h1")?.text())
        assertEquals(
            "Vous ne pouvez modifier que vos mandats, tant qu'ils ne sont pas approuvés par le client.",
            page.selectFirst("[data-edit-locked] p")?.text(),
        )
    }

    // mandate.js

    @Test
    fun `script and hooks`() {
        val page = render(MANDATE)

        assertTrue(page.selectFirst("script[src=/assets/dist/app.min.js]")!!.hasAttr("defer"))
        assertNotNull(page.selectFirst("script[src*=slim-select@4.3.0]"))
        assertNotNull(page.selectFirst("link[href*=slim-select@4.3.0]"))
        assertEquals("mandate-edit", page.body().attr("data-page"))
        assertEquals(",", page.body().attr("data-decimal-separator"))
        val form = page.selectFirst("form[data-edit-form]")!!
        assertEquals("/locations", form.attr("data-locations-url"))
        // #304: the script reads NdUtil.propertyRules; the type radios no longer carry their categories.
        assertTrue(form.select("[data-categories]").isEmpty())
        assertTrue(form.select("[data-rental-categories]").isEmpty())
        assertNotNull(form.selectFirst("[data-group=propertyType] [data-tenant-only]"))
        assertNotNull(form.selectFirst("[data-rate]"))
        assertEquals(5, form.select("[data-edit-group] [data-group-value]").size)
        assertEquals(DOUALA.toString(), form.selectFirst("[data-city-id]")?.attr("data-city-id"))
        assertNotNull(form.selectFirst("[data-group=rooms]"))
        assertNotNull(form.selectFirst("[data-group=furnitureType]"))
        assertNotNull(form.selectFirst("[data-group=parkings]"))
    }
}
