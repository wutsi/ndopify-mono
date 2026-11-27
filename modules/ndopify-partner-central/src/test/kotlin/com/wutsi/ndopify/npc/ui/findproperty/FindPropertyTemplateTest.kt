package com.wutsi.ndopify.npc.ui.findproperty

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.mandate.dto.FinancingType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.npc.ui.ScriptFixtures
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import com.wutsi.ndopify.web.core.service.TenantContext
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
import org.springframework.web.client.HttpClientErrorException
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

    private val buildingSale = complete.copy(
        listingType = ListingType.SALE,
        propertyCategory = PropertyCategory.COMMERCIAL,
        propertyType = "BUILDING",
        rooms = null,
        furnitureType = null,
        parkings = null,
        levels = 3,
        units = 12,
        area = 850,
        priceAmount = 250_000_000,
        financingType = FinancingType.CASH,
    )

    private val tenant = TENANT.copy(
        name = "Ndopify Cameroun",
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
        doReturn(TENANT).whenever(tenantContext).getTenant()

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
    fun `the need step asks to rent or buy`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, PendingMandate(listingType = ListingType.SALE))

        val page = render(get("/find-property?step=need"))

        assertEquals(
            listOf("RENTAL", "SALE"),
            page.select("input[name=listingType]").map { it.attr("value") },
        )
        assertTrue(page.selectFirst("input[name=listingType][value=SALE]")!!.hasAttr("checked"))
        assertFalse(page.selectFirst("input[name=listingType][value=RENTAL]")!!.hasAttr("checked"))
        val fieldset = page.selectFirst("[data-group=listingType]")!!.text()
        assertTrue(fieldset.contains("Louer"))
        assertTrue(fieldset.contains("Acheter"))
        assertTrue(fieldset.contains("Maison, appartement, terrain"))
        assertNotNull(page.selectFirst("#listingType-error"))
    }

    @Test
    fun needPrefilled() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property?step=need"))

        assertTrue(page.selectFirst("input[name=listingType][value=RENTAL]")!!.hasAttr("checked"))
        assertTrue(page.selectFirst("input[name=propertyCategory][value=RESIDENTIAL]")!!.hasAttr("checked"))
        assertFalse(page.selectFirst("input[name=propertyCategory][value=COMMERCIAL]")!!.hasAttr("checked"))
    }

    @Test
    fun `starting again shows an empty need step`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property"))

        assertEquals("Étape 1 sur 7", page.selectFirst("[data-progress-label]")?.text())
        assertTrue(page.select("input[name=listingType][checked]").isEmpty())
        assertTrue(page.select("input[name=propertyCategory][checked]").isEmpty())
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
        assertEquals("/find-property?step=need", page.selectFirst("[data-prev]")?.attr("href"))
    }

    @Test
    fun propertyShowsOnlyCategoryTypes() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = null, rooms = null),
        )

        val page = render(get("/find-property?step=property"))

        val types = page.select("input[name=propertyType]").map { it.attr("value") }
        assertEquals(listOf("RETAIL", "OFFICE", "INDUSTRIAL", "ANY"), types)
        assertNull(page.selectFirst("[data-group=rooms]"))
        assertNull(page.selectFirst("[data-group=furnitureType]"))
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
    fun `a sale property step offers land, and asks its rooms and its furniture`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(listingType = ListingType.SALE, propertyType = null, rooms = null),
        )

        val page = render(get("/find-property?step=property"))

        assertEquals(
            listOf("STUDIO", "APARTMENT", "HOUSE", "VILLA", "DUPLEX", "LAND", "ANY"),
            page.select("input[name=propertyType]").map { it.attr("value") },
        )
        assertNotNull(page.selectFirst("[data-group=rooms]"))
        assertFalse(page.selectFirst("[data-group=furnitureType]")!!.hasAttr("hidden"))
    }

    @Test
    fun `a commercial purchase property step offers land under both usages`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.COMMERCIAL,
                propertyType = null,
                rooms = null,
            ),
        )

        val page = render(get("/find-property?step=property"))

        assertEquals(
            listOf("RETAIL", "OFFICE", "INDUSTRIAL", "LAND", "BUILDING", "ANY"),
            page.select("input[name=propertyType]").map { it.attr("value") },
        )
    }

    @Test
    fun `a land purchase asks its documents and a required surface`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = "LAND",
                rooms = null,
                furnitureType = null,
                area = null,
            ),
        )

        val page = render(get("/find-property?step=property"))

        val documents = assertNotNull(page.selectFirst("[data-group=titleDocuments]"))
        assertFalse(documents.hasAttr("hidden"))
        assertEquals(
            listOf("LAND_TITLE", "SURVEY_PLAN", "CERTIFICATE_OF_OWNERSHIP"),
            documents.select("input[name=titleDocuments]").map { it.attr("value") },
        )
        assertEquals("Surface du terrain", page.selectFirst("[data-area-label]")?.text())
        assertNull(page.selectFirst("[data-group=area] .field__optional"))
    }

    @Test
    fun `a rental is asked no document`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                listingType = ListingType.RENTAL,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = "LAND",
                rooms = null,
                furnitureType = null,
                area = null,
            ),
        )

        val page = render(get("/find-property?step=property"))

        assertTrue(page.selectFirst("[data-group=titleDocuments]")!!.hasAttr("hidden"))
        assertEquals("Surface du terrain minimale", page.selectFirst("[data-area-label]")?.text())
    }

    @Test
    fun `a purchase asks its documents whatever its type`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = "HOUSE",
                furnitureType = "UNFURNISHED",
                financingType = FinancingType.CASH,
            ),
        )

        val page = render(get("/find-property?step=property"))

        assertNotNull(page.selectFirst("[data-group=titleDocuments]:not([hidden])"))
        assertNotNull(page.selectFirst("input[name=titleDocuments][value=LAND_TITLE]"))
    }

    @Test
    fun `a residential purchase asks furniture and no surface, a commercial type asks its surface`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = "HOUSE",
                furnitureType = "UNFURNISHED",
                financingType = FinancingType.CASH,
            ),
        )

        val house = render(get("/find-property?step=property"))

        assertFalse(house.selectFirst("[data-group=furnitureType]")!!.hasAttr("hidden"))
        assertTrue(house.selectFirst("[data-group=area]")!!.hasAttr("hidden"))

        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.COMMERCIAL,
                propertyType = "OFFICE",
                rooms = null,
                furnitureType = null,
                financingType = FinancingType.CASH,
            ),
        )

        val office = render(get("/find-property?step=property"))

        assertNull(office.selectFirst("[data-group=furnitureType]"))
        assertFalse(office.selectFirst("[data-group=area]")!!.hasAttr("hidden"))
    }

    @Test
    fun `a rental office asks no parkings`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                listingType = ListingType.RENTAL,
                propertyCategory = PropertyCategory.COMMERCIAL,
                propertyType = "OFFICE",
                rooms = null,
                furnitureType = null,
            ),
        )

        val page = render(get("/find-property?step=property"))

        assertTrue(page.select("[data-group=parkings]").attr("hidden").isNotEmpty())
    }

    @Test
    fun `a land purchase shows its saved documents ticked`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = "LAND",
                rooms = null,
                furnitureType = null,
                parkings = null,
                area = 400,
                financingType = FinancingType.CASH,
                titleDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
            ),
        )

        val page = render(get("/find-property?step=property"))

        assertTrue(page.selectFirst("input[name=titleDocuments][value=LAND_TITLE]")!!.hasAttr("checked"))
        assertTrue(page.selectFirst("input[name=titleDocuments][value=SURVEY_PLAN]")!!.hasAttr("checked"))
        assertFalse(page.selectFirst("input[name=titleDocuments][value=CERTIFICATE_OF_OWNERSHIP]")!!.hasAttr("checked"))
    }

    @Test
    fun `a building sale's property step asks its counts and no parking`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, buildingSale)

        val page = render(get("/find-property?step=property"))

        val counts = assertNotNull(page.selectFirst("[data-building-counts]"))
        assertFalse(counts.hasAttr("hidden"))
        assertEquals(
            "Nombre d'étages (1 à 30)",
            page.selectFirst("label[for=levels]")?.text()?.replace('\u00a0', ' '),
        )
        assertEquals(
            "Nombre d'unités (1 à 500)",
            page.selectFirst("label[for=units]")?.text()?.replace('\u00a0', ' '),
        )
        assertEquals("3", page.selectFirst("#levels")?.attr("value"))
        assertEquals("12", page.selectFirst("#units")?.attr("value"))
        assertEquals(2, page.select("input[name=levelsAny], input[name=unitsAny]").size)
        assertEquals("checkbox", page.selectFirst("input[name=levelsAny]")?.attr("type"))
        assertEquals("checkbox", page.selectFirst("input[name=unitsAny]")?.attr("type"))
        assertTrue(page.selectFirst("[data-group=parkings]")!!.hasAttr("hidden"))
    }

    @Test
    fun `a rental is asked no building counts`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                propertyCategory = PropertyCategory.COMMERCIAL,
                propertyType = "OFFICE",
                rooms = null,
                furnitureType = null,
            ),
        )

        val page = render(get("/find-property?step=property"))

        assertTrue(page.selectFirst("[data-building-counts]")!!.hasAttr("hidden"))
    }

    @Test
    fun `a building sale's review reads its counts once and no amenity row`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, buildingSale)

        val page = render(get("/find-property?step=review"))

        val terms = page.select(".review__term").map { it.text() }
        assertFalse(terms.contains("Étages"))
        assertFalse(terms.contains("Unités"))
        assertFalse(terms.contains("Indispensable"))
        assertEquals("Immeuble · 3 étages · 12 unités · 850 m² minimum", value(page, "property"))
    }

    @Test
    fun `a building's review reads its counts once, with the budget's price per unit`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, buildingSale)

        val page = render(get("/find-property?step=review"))

        assertEquals(
            "Immeuble · 3 étages · 12 unités · 850 m² minimum",
            page.selectFirst("[data-review-value=property]")!!.text(),
        )
        assertNull(page.selectFirst("[data-review-value=levels]"))
        assertTrue(
            page.select("[data-review-value=price]").text().contains("≈ 20,83M FCFA/unité pour 12 unités"),
        )
    }

    @Test
    fun `a building sale indifferent counts read Indifferent on the review`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            buildingSale.copy(levels = null, levelsAny = true, units = null, unitsAny = true),
        )

        val page = render(get("/find-property?step=review"))

        assertEquals(
            "Immeuble · étages indifférents · unités indifférentes · 850 m² minimum",
            value(page, "property"),
        )
        assertFalse(page.select("[data-review-value=price]").text().contains("/unité"))
    }

    @Test
    fun `the progress follows the building sale's six steps`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, buildingSale)

        val page = render(get("/find-property?step=budget"))

        assertEquals("budget", page.selectFirst("[data-step]")?.attr("data-step"))
        assertEquals("Étape 4 sur 6", page.selectFirst("[data-progress-label]")?.text())
        assertEquals("6", page.selectFirst("[data-progress]")?.attr("aria-valuemax"))
        assertEquals("Étape 4 sur 6 : votre budget et votre date d'emménagement", page.selectFirst("[data-step] legend")?.text())
    }

    @Test
    fun `a building sale's previous links follow its six steps`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, buildingSale)

        // Budget skips the amenities step the branch doesn't have; contact still goes back to budget.
        assertEquals(
            "/find-property?step=property",
            render(get("/find-property?step=budget")).selectFirst("[data-prev]")?.attr("href"),
        )
        assertEquals(
            "/find-property?step=budget",
            render(get("/find-property?step=contact")).selectFirst("[data-prev]")?.attr("href"),
        )
    }

    @Test
    fun `every other branch keeps the seven steps`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property?step=budget"))

        assertEquals("Étape 5 sur 7", page.selectFirst("[data-progress-label]")?.text())
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
    fun `a sale budget step words a purchase and asks its financing`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                listingType = ListingType.SALE,
                priceAmount = 10_000_000,
                movingPeriod = MovingPeriod.WITHIN_3_MONTHS,
                financingType = FinancingType.CASH,
            ),
        )

        val page = render(get("/find-property?step=budget"))

        assertEquals("10 000 000", page.selectFirst("#priceAmount")?.attr("value"))
        assertEquals("Budget total", page.selectFirst("[data-price-label]")?.text())
        assertEquals("FCFA", page.selectFirst("[data-price-suffix]")?.text())
        assertEquals("Le prix maximal que vous êtes prêt à payer, et quand vous pensez acheter.", page.selectFirst("[data-budget-intro]")?.text())
        assertEquals(
            "Quand souhaitez-vous acheter ?",
            page.selectFirst("[data-move-in-label]")?.text()?.replace('\u00a0', ' '),
        )
        val financing = assertNotNull(page.selectFirst("[data-group=financingType]"))
        assertFalse(financing.hasAttr("hidden"))
        assertEquals(
            listOf("CASH", "BANK_LOAN", "TRANSFER_FROM_ABROAD", "UNDECIDED"),
            financing.select("input[name=financingType]").map { it.attr("value") },
        )
        assertTrue(page.selectFirst("input[name=financingType][value=CASH]")!!.hasAttr("checked"))
        assertTrue(page.selectFirst("input[name=financingType][value=CASH]")!!.hasAttr("required"))
        val perSqm = assertNotNull(page.selectFirst("[data-price-per-sqm]"))
        assertTrue(perSqm.hasAttr("hidden"))
        assertEquals("", perSqm.text())
    }

    @Test
    fun `the forms carry the listing type for the script`() {
        val rental = render(get("/find-property"))
        assertEquals("RENTAL", form(rental).attr("data-listing-type"))

        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            PendingMandate(listingType = ListingType.SALE),
        )

        val sale = render(get("/find-property?step=need"))

        assertEquals("SALE", form(sale).attr("data-listing-type"))
    }

    @Test
    fun `the sale budget step has the price per square metre hook`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = "LAND",
                area = 400,
                priceAmount = 10_000_000,
                financingType = FinancingType.CASH,
            ),
        )

        val page = render(get("/find-property?step=budget"))

        val perSqm = assertNotNull(page.selectFirst("[data-price-per-sqm]"))
        assertTrue(perSqm.hasAttr("hidden"))
        assertEquals("", perSqm.text())
        assertEquals("400", form(page).attr("data-area"))
        assertEquals("LAND", form(page).attr("data-property-type"))
        assertEquals("FCFA", form(page).attr("data-currency"))
        assertEquals(",", form(page).attr("data-decimal-separator"))
    }

    @Test
    fun `a rental budget step is today's and asks no financing`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property?step=budget"))

        assertEquals("Budget mensuel", page.selectFirst("[data-price-label]")?.text())
        assertEquals("FCFA / mois", page.selectFirst("[data-price-suffix]")?.text())
        assertEquals("Date d'emménagement souhaitée", page.selectFirst("[data-move-in-label]")?.text())
        assertNull(page.selectFirst("[data-group=financingType]"))
        assertNotNull(page.selectFirst("[data-price-per-sqm]"))
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
            "Appartement · 3 chambres · Meublé ou non · 1 place de parking",
            value(page, "property"),
        )
        assertEquals("Compteur d'eau, Gardien de sécurité", value(page, "amenities"))
        assertEquals("150K\u00a0FCFA par mois", value(page, "price"))
        assertEquals("Dans moins d'1\u00a0mois", value(page, "movingPeriod"))
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
    fun `a refused e-mail comes back to the contact step with its error`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)
        doThrow(badRequest(ErrorCode.EMAIL_ADDRESS_INVALID)).whenever(mandateClient).create(any())

        val page = render(post("/find-property/review"))

        assertEquals("contact", page.selectFirst("[data-step]")?.attr("data-step"))
        val error = assertNotNull(page.selectFirst("#email-error"))
        assertFalse(error.hasAttr("hidden"))
        assertEquals("Cette adresse email n'est pas valide.", error.text())
        assertEquals("true", page.selectFirst("#email")?.attr("aria-invalid"))
        assertEquals("Revenir à la revue", page.selectFirst("[data-next]")?.text())
        assertNull(page.selectFirst("[data-submit-error]"))
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
    fun `sale review words a purchase and its answers`() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                listingType = ListingType.SALE,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = "LAND",
                rooms = null,
                furnitureType = null,
                parkings = null,
                area = 400,
                priceAmount = 10_000_000,
                financingType = FinancingType.CASH,
                titleDocuments = emptyList(),
            ),
        )

        val page = render(get("/find-property?step=review"))

        assertEquals("Achat, usage résidentiel", value(page, "project"))
        assertEquals("Terrain · 400 m² minimum", value(page, "property"))
        assertTrue(value(page, "price").contains("10M\u00a0FCFA"))
        assertTrue(value(page, "price").contains("≈ 25K\u00a0FCFA/m² pour 400 m²"))
        assertEquals("Achat prévu", page.selectFirst("[data-review-move-in-term]")?.text())
        assertEquals("Comptant", value(page, "financing"))
        assertEquals("Aucun document exigé", value(page, "titleDocuments"))
        assertTrue(page.select("dt").any { it.text() == "Budget total" })
    }

    @Test
    fun `a rental review has no purchase rows`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property?step=review"))

        assertEquals("Location, usage résidentiel", value(page, "project"))
        assertTrue(page.select("dt").any { it.text() == "Budget mensuel" })
        assertNull(page.selectFirst("[data-review-value=financing]"))
        assertNull(page.selectFirst("[data-review-value=titleDocuments]"))
    }

    @Test
    fun `sale confirmation names the sales agent`() {
        session.setAttribute(
            SubmittedMandate.SESSION_ATTRIBUTE,
            SubmittedMandate("ray@gmail.com", "+237670000001", ContactPeriodType.EVENING, ListingType.SALE),
        )

        val page = render(get("/find-property?step=confirmation"))

        val text = page.text()
        assertTrue(text.contains("Un agent vente vous est attribué"))
        assertTrue(text.contains("qui traite les ventes"))
        assertTrue(text.contains("Vous visitez des biens à acheter"))
        assertTrue(text.contains("signature de la vente"))
        assertFalse(text.contains("Un agent vous est attribué"))
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

    @Test
    fun `every step after the first shows the project pill`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(listingType = ListingType.SALE))

        val page = render(get("/find-property?step=area"))

        val pill = assertNotNull(page.selectFirst(".progress__head .project-tag"))
        assertEquals("Achat", pill.selectFirst("[data-project-label]")?.text())
        assertEquals("p", pill.tagName())
        assertEquals("", pill.attr("role"))
        assertEquals("", pill.attr("tabindex"))
        assertNull(pill.selectFirst("a, button"))
    }

    @Test
    fun `a rental step reads Location`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val page = render(get("/find-property?step=area"))

        assertEquals("Location", page.selectFirst(".progress__head [data-project-label]")?.text())
    }

    @Test
    fun `step 1 shows no project pill`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, PendingMandate(listingType = ListingType.SALE))

        assertNull(render(get("/find-property?step=need")).selectFirst(".project-tag"))
    }

    @Test
    fun `the confirmation shows the submitted project under the title`() {
        session.setAttribute(
            SubmittedMandate.SESSION_ATTRIBUTE,
            SubmittedMandate("ray@gmail.com", "+237670000001", ContactPeriodType.EVENING, ListingType.SALE),
        )

        val page = render(get("/find-property?step=confirmation"))

        assertEquals("Achat", page.selectFirst(".confirmation__head [data-project-label]")?.text())
        assertEquals("Demande envoyée", page.selectFirst("#confirmation-title")?.text())
    }

    @Test
    fun `renders the steps the page-script test runs find-property js on`() {
        val residentialSale = complete.copy(listingType = ListingType.SALE, propertyType = null, furnitureType = null)
        val steps = mapOf(
            "find-property-residential-sale" to ("property" to residentialSale),
            "find-budget-building-sale" to ("budget" to buildingSale),
        )
        for ((name, step) in steps) {
            session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, step.second)
            ScriptFixtures.write(name, render(get("/find-property?step=${step.first}")))
        }
    }

    private fun badRequest(code: String): HttpClientErrorException {
        val ex = HttpClientErrorException(HttpStatus.BAD_REQUEST)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        return ex
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
