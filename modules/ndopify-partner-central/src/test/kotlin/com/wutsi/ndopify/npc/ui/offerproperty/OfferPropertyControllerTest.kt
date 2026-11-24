package com.wutsi.ndopify.npc.ui.offerproperty

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.npc.ui.findproperty.PendingMandate
import com.wutsi.ndopify.npc.ui.findproperty.SubmittedMandate
import com.wutsi.ndopify.npc.ui.login.LoginController
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import com.wutsi.ndopify.web.core.service.TenantContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfferPropertyControllerTest {
    companion object {
        const val YAOUNDE = 2220957L
        const val DOUALA = 2232593L
        const val MONTREAL = 6077243L
        val TODAY: LocalDate = LocalDate.of(2026, 10, 21)
    }

    private val mandateClient = mock<NdMandateClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var session: MockHttpSession
    private lateinit var mvc: MockMvc

    private val locations = listOf(
        Location(id = YAOUNDE, name = "Yaoundé", type = LocationType.CITY, country = "CM"),
        Location(id = DOUALA, name = "Douala", type = LocationType.CITY, country = "CM"),
        Location(id = MONTREAL, name = "Montréal", type = LocationType.CITY, country = "CA"),
        Location(id = 1001, parentId = YAOUNDE, name = "Bastos", type = LocationType.NEIGHBORHOOD, country = "CM"),
        Location(id = 2001, parentId = DOUALA, name = "Akwa", type = LocationType.NEIGHBORHOOD, country = "CM"),
    )

    private val complete = PendingListingTest.COMPLETE

    private val sale = complete.copy(
        listingType = ListingType.SALE,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        propertyType = "LAND",
        area = 800,
        priceAmount = 45_000_000L,
        advanceRent = null,
        securityDeposit = null,
        rooms = null,
        furnitureType = null,
        parkings = null,
    )

    private val buildingSale = complete.copy(
        listingType = ListingType.SALE,
        propertyCategory = PropertyCategory.COMMERCIAL,
        propertyType = "BUILDING",
        area = 850,
        priceAmount = 250_000_000L,
        advanceRent = null,
        securityDeposit = null,
        rooms = null,
        furnitureType = null,
        parkings = null,
        levels = 3,
        units = 12,
        roadAccess = null,
    )

    @BeforeEach
    fun setUp() {
        doReturn(TENANT).whenever(tenantContext).getTenant()
        whenever(locationClient.search(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<SearchLocationRequest>(0)
            SearchLocationResponse(locations = locations.filter { request.ids.contains(it.id) })
        }

        val clock = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC)
        val controller = OfferPropertyController(mandateClient, locationClient, tenantContext, clock)
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
        session = MockHttpSession()
    }

    // GET /offer-property

    @Test
    fun show() {
        val result = mvc.perform(get("/offer-property").session(session)).andReturn()

        assertEquals("offer-property", result.modelAndView?.viewName)
        assertEquals("property", model(result)["step"])
        assertEquals(1, model(result)["stepNumber"])
        assertEquals(7, model(result)["stepCount"])
        assertEquals(PendingListing(), model(result)["answers"])
        assertEquals(emptyMap<String, String>(), model(result)["errors"])
        assertEquals(false, model(result)["returning"])
        assertEquals(TENANT, model(result)["tenant"])
    }

    @Test
    fun exitUrlIsTheOwnerPageOfThePublicPortal() {
        doReturn(TENANT.copy(publicPortalUrl = "https://www.ndopify.com/")).whenever(tenantContext).getTenant()

        assertEquals(
            "https://www.ndopify.com/owner",
            model(mvc.perform(get("/offer-property").session(session)).andReturn())["exitUrl"],
        )

        session.setAttribute(OfferPropertyController.SUBMITTED_ATTRIBUTE, submitted())
        val confirmation = mvc.perform(get("/offer-property?step=confirmation").session(session)).andReturn()
        assertEquals("confirmation", model(confirmation)["step"])
        assertEquals("https://www.ndopify.com/owner", model(confirmation)["exitUrl"])
    }

    @Test
    fun `the project label follows the saved project`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, sale)
        val saleStep = mvc.perform(get("/offer-property?step=features").session(session)).andReturn()
        assertEquals("Vente", model(saleStep)["projectLabel"])

        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)
        val rentalStep = mvc.perform(get("/offer-property?step=features").session(session)).andReturn()
        assertEquals("Location", model(rentalStep)["projectLabel"])
    }

    @Test
    fun `the confirmation carries the submitted project label`() {
        session.setAttribute(
            OfferPropertyController.SUBMITTED_ATTRIBUTE,
            SubmittedMandate("ray@gmail.com", "+237670000001", ContactPeriodType.EVENING, ListingType.SALE),
        )
        val result = mvc.perform(get("/offer-property?step=confirmation").session(session)).andReturn()
        assertEquals("Vente", model(result)["projectLabel"])
    }

    @Test
    fun noExitUrlWithoutPublicPortal() {
        doReturn(TENANT.copy(publicPortalUrl = " ")).whenever(tenantContext).getTenant()

        val result = mvc.perform(get("/offer-property").session(session)).andReturn()

        assertNull(model(result)["exitUrl"])
    }

    @Test
    fun showSkipsToTheFirstIncompleteStep() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(priceAmount = null))

        val result = mvc.perform(get("/offer-property?step=contact").session(session)).andReturn()

        assertEquals("terms", model(result)["step"])
        assertEquals("FCFA", model(result)["currency"])
        assertEquals("2026-10-21", model(result)["availableMin"])
        assertEquals("2027-10-21", model(result)["availableMax"])
    }

    @Test
    fun showAddress() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/offer-property?step=address").session(session)).andReturn()

        assertEquals("address", model(result)["step"])
        assertEquals("Yaoundé", (model(result)["city"] as Location).name)
        assertEquals("Bastos", (model(result)["neighborhood"] as Location).name)
    }

    @Test
    fun showFeatures() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/offer-property?step=features").session(session)).andReturn()

        assertEquals(
            // #304: a rental offers neither land nor a building.
            listOf("APARTMENT", "HOUSE", "ROOM", "STUDIO", "DUPLEX"),
            (model(result)["propertyTypes"] as List<*>).map { it.toString() },
        )
        assertTrue((model(result)["propertyTypeLabels"] as Map<*, *>).isNotEmpty())
    }

    @Test
    fun returnToReviewComesBackToTheReview() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)

        val shown = mvc.perform(get("/offer-property?step=terms&returnTo=review").session(session)).andReturn()
        assertEquals(true, model(shown)["returning"])

        val saved = mvc.perform(
            post("/offer-property/terms?returnTo=review").session(session)
                .param("priceAmount", "200 000")
                .param("advanceRent", "")
                .param("securityDeposit", "2")
                .param("availableFrom", "2026-10-25"),
        ).andReturn()
        assertEquals("redirect:/offer-property?step=review", saved.modelAndView?.viewName)
    }

    // POST /offer-property/property

    @Test
    fun propertySavesAndGoesToAddress() {
        val result = mvc.perform(
            post("/offer-property/property").session(session)
                .param("listingType", "RENTAL").param("propertyCategory", "RESIDENTIAL"),
        ).andReturn()

        assertEquals("redirect:/offer-property?step=address", result.modelAndView?.viewName)
        assertEquals(ListingType.RENTAL, pending()?.listingType)
        assertEquals(PropertyCategory.RESIDENTIAL, pending()?.propertyCategory)
    }

    @Test
    fun propertyWithoutCategory() {
        val result = mvc.perform(
            post("/offer-property/property").session(session).param("listingType", "RENTAL"),
        ).andReturn()

        assertEquals("property", model(result)["step"])
        assertEquals("Indiquez si le bien est pour y habiter ou pour une activité.", errors(result)["propertyCategory"])
        assertNull(pending())
    }

    @Test
    fun propertyWithoutProject() {
        val result = mvc.perform(
            post("/offer-property/property").session(session).param("propertyCategory", "RESIDENTIAL"),
        ).andReturn()

        assertEquals("property", model(result)["step"])
        assertEquals(
            "Indiquez si vous souhaitez mettre votre bien en location ou le vendre.",
            errors(result)["listingType"],
        )
        assertNull(pending())
    }

    @Test
    fun `the type parameter preselects the project`() {
        val result = mvc.perform(get("/offer-property?type=SALE").session(session)).andReturn()

        assertEquals(ListingType.SALE, (model(result)["answers"] as PendingListing).listingType)
        assertEquals(true, model(result)["sale"])
    }

    @Test
    fun `starting a new run drops the answers of the previous one`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/offer-property").session(session)).andReturn()

        assertEquals("property", model(result)["step"])
        assertEquals(PendingListing(), model(result)["answers"])
        assertNull(pending())
    }

    @Test
    fun `starting a new run drops the previous confirmation`() {
        session.setAttribute(OfferPropertyController.SUBMITTED_ATTRIBUTE, submitted())

        val result = mvc.perform(get("/offer-property").session(session)).andReturn()

        assertEquals("property", model(result)["step"])
        assertNull(session.getAttribute(OfferPropertyController.SUBMITTED_ATTRIBUTE))
    }

    @Test
    fun `the type parameter of a new run wins over the previous project`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, PendingListing(listingType = ListingType.RENTAL))

        val result = mvc.perform(get("/offer-property").param("type", "SALE").session(session)).andReturn()

        assertEquals(ListingType.SALE, (model(result)["answers"] as PendingListing).listingType)
        assertNull(pending())
    }

    @Test
    fun `an in-run URL ignores the type parameter`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, PendingListing(listingType = ListingType.RENTAL))

        val result = mvc.perform(get("/offer-property?step=property&type=SALE").session(session)).andReturn()

        assertEquals("property", model(result)["step"])
        assertEquals(ListingType.RENTAL, (model(result)["answers"] as PendingListing).listingType)
        assertEquals(PendingListing(listingType = ListingType.RENTAL), pending())
    }

    @Test
    fun `going back to the property step keeps the answers of the current run`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/offer-property?step=property").session(session)).andReturn()

        assertEquals("property", model(result)["step"])
        assertEquals(complete, model(result)["answers"])
        assertEquals(complete, pending())
    }

    @Test
    fun `a step posted with its errors leaves the run untouched`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(post("/offer-property/terms").session(session)).andReturn()

        assertEquals("terms", model(result)["step"])
        assertTrue(errors(result).isNotEmpty())
        assertEquals(complete, pending())
    }

    @Test
    fun `starting a run keeps the other wizard's answers`() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, PendingMandate())

        mvc.perform(get("/offer-property").session(session)).andReturn()

        assertEquals(PendingMandate(), session.getAttribute(PendingMandate.SESSION_ATTRIBUTE))
    }

    @Test
    fun `an unknown type parameter is ignored`() {
        val result = mvc.perform(get("/offer-property?type=ANY").session(session)).andReturn()

        assertNull((model(result)["answers"] as PendingListing).listingType)
    }

    @Test
    fun `the exit link follows the project`() {
        doReturn(TENANT.copy(publicPortalUrl = "https://www.ndopify.com/")).whenever(tenantContext).getTenant()
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(listingType = ListingType.SALE))

        assertEquals(
            "https://www.ndopify.com/owner/sell",
            model(mvc.perform(get("/offer-property?step=features").session(session)).andReturn())["exitUrl"],
        )
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)
        assertEquals(
            "https://www.ndopify.com/owner",
            model(mvc.perform(get("/offer-property?step=features").session(session)).andReturn())["exitUrl"],
        )
    }

    // POST /offer-property/address

    @Test
    fun addressSaves() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, PendingListing(listingType = ListingType.RENTAL, propertyCategory = PropertyCategory.RESIDENTIAL))

        val result = mvc.perform(
            post("/offer-property/address").session(session)
                .param("cityId", "$YAOUNDE").param("neighborhoodId", "1001"),
        ).andReturn()

        assertEquals("redirect:/offer-property?step=terms", result.modelAndView?.viewName)
        assertEquals(1001L, pending()?.neighborhoodId)
    }

    @Test
    fun addressRejectsANeighborhoodOfAnotherCity() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, PendingListing(listingType = ListingType.RENTAL, propertyCategory = PropertyCategory.RESIDENTIAL))

        val result = mvc.perform(
            post("/offer-property/address").session(session)
                .param("cityId", "$YAOUNDE").param("neighborhoodId", "2001"),
        ).andReturn()

        assertEquals("address", model(result)["step"])
        assertEquals("Choisissez un quartier de la ville sélectionnée.", errors(result)["neighborhoodId"])
        assertNull(pending()?.cityId)
    }

    @Test
    fun addressRejectsACityOfAnotherCountry() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, PendingListing(listingType = ListingType.RENTAL, propertyCategory = PropertyCategory.RESIDENTIAL))

        val result = mvc.perform(
            post("/offer-property/address").session(session)
                .param("cityId", "$MONTREAL").param("neighborhoodId", "1001"),
        ).andReturn()

        assertEquals("Cette ville n'est pas proposée. Choisissez-en une dans la liste.", errors(result)["cityId"])
    }

    @Test
    fun addressBeforePropertyRedirects() {
        val result = mvc.perform(
            post("/offer-property/address").session(session)
                .param("cityId", "$YAOUNDE").param("neighborhoodId", "1001"),
        ).andReturn()

        assertEquals("redirect:/offer-property?step=property", result.modelAndView?.viewName)
    }

    // POST /offer-property/terms

    private fun terms(
        price: String = "150 000",
        advance: String = "3",
        deposit: String = "2",
        date: String = "2026-10-31",
    ) = post("/offer-property/terms").session(session)
        .param("priceAmount", price)
        .param("advanceRent", advance)
        .param("securityDeposit", deposit)
        .param("availableFrom", date)

    @Test
    fun termsSaves() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(priceAmount = null))

        val result = mvc.perform(terms()).andReturn()

        assertEquals("redirect:/offer-property?step=features", result.modelAndView?.viewName)
        assertEquals(150_000L, pending()?.priceAmount)
        assertEquals(3, pending()?.advanceRent)
        assertEquals(2, pending()?.securityDeposit)
        assertEquals(LocalDate.of(2026, 10, 31), pending()?.availableFrom)
    }

    @Test
    fun termsWithoutAdvance() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(priceAmount = null))

        mvc.perform(terms(advance = "")).andReturn()

        assertNull(pending()?.advanceRent)
        assertEquals(150_000L, pending()?.priceAmount)
    }

    @Test
    fun termsWithAnUnparsableDate() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(priceAmount = null))

        val result = mvc.perform(terms(date = "2026-02-31")).andReturn()

        assertEquals("terms", model(result)["step"])
        assertEquals("Choisissez une date entre aujourd'hui et dans 365 jours.", errors(result)["availableFrom"])
        assertNull(pending()?.priceAmount)
    }

    @Test
    fun termsWithADatePast365Days() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(priceAmount = null))

        val result = mvc.perform(terms(date = "2027-10-23")).andReturn()

        assertEquals("Choisissez une date entre aujourd'hui et dans 365 jours.", errors(result)["availableFrom"])
    }

    @Test
    fun termsBelowTheMinimumRent() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(priceAmount = null))

        val result = mvc.perform(terms(price = "1 000")).andReturn()

        assertEquals("Indiquez un loyer d'au moins ${FORMATTER.compactMoney(5000)} par mois.", errors(result)["priceAmount"])
    }

    @Test
    fun `a sale refuses a price below the floor`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, sale.copy(priceAmount = null))

        val result = mvc.perform(
            post("/offer-property/terms").session(session)
                .param("priceAmount", "900 000").param("availableFrom", "2026-10-31"),
        ).andReturn()

        assertEquals("terms", model(result)["step"])
        assertEquals(
            "Indiquez un prix d'au moins ${FORMATTER.compactMoney(1_000_000)}.",
            errors(result)["priceAmount"],
        )
        assertNull(pending()?.priceAmount)
    }

    @Test
    fun `a sale needs no advance nor deposit`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, sale.copy(priceAmount = null))

        val result = mvc.perform(
            post("/offer-property/terms").session(session)
                .param("priceAmount", "45 000 000").param("advanceRent", "2").param("securityDeposit", "2")
                .param("availableFrom", "2026-10-31"),
        ).andReturn()

        assertEquals("redirect:/offer-property?step=features", result.modelAndView?.viewName)
        assertEquals(45_000_000L, pending()?.priceAmount)
        assertNull(pending()?.advanceRent)
        assertNull(pending()?.securityDeposit)
    }

    @Test
    fun `a rental keeps its advance, deposit and floor`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(priceAmount = null))

        val below = mvc.perform(terms(price = "4 000")).andReturn()
        assertEquals("Indiquez un loyer d'au moins ${FORMATTER.compactMoney(5000)} par mois.", errors(below)["priceAmount"])

        val saved = mvc.perform(terms()).andReturn()
        assertEquals("redirect:/offer-property?step=features", saved.modelAndView?.viewName)
        assertEquals(3, pending()?.advanceRent)
        assertEquals(2, pending()?.securityDeposit)
    }

    // GET /offer-property?step=features

    @Test
    fun `a sale's feature flags follow the saved type`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, sale.copy(propertyType = null))
        val first = model(mvc.perform(get("/offer-property?step=features").session(session)).andReturn())
        assertEquals(false, first["needsFurniture"])
        assertEquals(false, first["requiresRooms"])
        assertEquals(false, first["requiresParking"])
        assertEquals(false, first["asksArea"])
        assertEquals(true, first["asksOwnershipDocuments"])

        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, sale)
        val land = model(mvc.perform(get("/offer-property?step=features").session(session)).andReturn())
        assertEquals(false, land["needsFurniture"])
        assertEquals(false, land["requiresRooms"])
        assertEquals(false, land["requiresParking"])
        assertEquals(true, land["asksArea"])
        assertEquals(true, land["asksOwnershipDocuments"])

        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, sale.copy(propertyType = "HOUSE", area = null))
        val house = model(mvc.perform(get("/offer-property?step=features").session(session)).andReturn())
        assertEquals(true, house["needsFurniture"])
        assertEquals(true, house["requiresRooms"])
        assertEquals(true, house["requiresParking"])
        assertEquals(false, house["asksArea"])
        assertEquals(true, house["asksOwnershipDocuments"])

        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)
        val rental = model(mvc.perform(get("/offer-property?step=features").session(session)).andReturn())
        assertEquals(true, rental["needsFurniture"])
        assertEquals(true, rental["requiresRooms"])
        assertEquals(true, rental["requiresParking"])
        assertEquals(false, rental["asksArea"])
        assertEquals(false, rental["asksOwnershipDocuments"])
    }

    // POST /offer-property/features

    private fun features(vararg params: Pair<String, String>) =
        post("/offer-property/features").session(session).also { request ->
            params.forEach { (name, value) -> request.param(name, value) }
        }

    @Test
    fun featuresDropsWhatDoesNotApply() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null, parkings = null))

        val result = mvc.perform(
            features(
                "propertyType" to "ROOM",
                "furnitureType" to "FULLY_FURNISHED",
                "rooms" to "3",
                "hasParking" to "YES",
                "parkings" to "2",
            ),
        ).andReturn()

        assertEquals("redirect:/offer-property?step=amenities", result.modelAndView?.viewName)
        assertEquals("ROOM", pending()?.propertyType)
        // A room has no number of rooms, but it does ask its parking and its furniture (#304).
        assertNull(pending()?.rooms)
        assertEquals("2", pending()?.parkings)
        assertEquals("FULLY_FURNISHED", pending()?.furnitureType)
    }

    @Test
    fun featuresParkingNo() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null, parkings = null))

        mvc.perform(
            features(
                "propertyType" to "APARTMENT",
                "furnitureType" to "UNFURNISHED",
                "rooms" to "2",
                "hasParking" to "NO",
                "parkings" to "2",
            ),
        ).andReturn()

        assertEquals("0", pending()?.parkings)
    }

    @Test
    fun featuresParkingYesWithoutPlaces() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null, parkings = null))

        val result = mvc.perform(
            features(
                "propertyType" to "APARTMENT",
                "furnitureType" to "UNFURNISHED",
                "rooms" to "2",
                "hasParking" to "YES",
            ),
        ).andReturn()

        assertEquals("features", model(result)["step"])
        assertEquals("Indiquez le nombre de places.", errors(result)["parkings"])
        assertNull(errors(result)["hasParking"])
        assertEquals("YES", model(result)["hasParking"])
        assertNull(pending()?.propertyType)
    }

    @Test
    fun featuresWithoutParkingAnswer() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null, parkings = null))

        val result = mvc.perform(
            features("propertyType" to "APARTMENT", "furnitureType" to "UNFURNISHED", "rooms" to "2"),
        ).andReturn()

        assertEquals("Indiquez si le bien a un parking ou un garage.", errors(result)["hasParking"])
        assertNull(errors(result)["parkings"])
        assertNull(model(result)["hasParking"])
    }

    @Test
    fun featuresCommercialRequiresTheArea() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            complete.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = null, rooms = null, parkings = null),
        )

        val missing = mvc.perform(features("propertyType" to "OFFICE")).andReturn()
        assertEquals("Indiquez la surface, même approximative.", errors(missing)["area"])

        val saved = mvc.perform(features("propertyType" to "OFFICE", "area" to "1 200")).andReturn()
        assertEquals("redirect:/offer-property?step=amenities", saved.modelAndView?.viewName)
        assertEquals(1200, pending()?.area)
    }

    @Test
    fun `a land sale requires its surface`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            sale.copy(propertyType = null, area = null),
        )

        val result = mvc.perform(features("propertyType" to "LAND")).andReturn()

        assertEquals("features", model(result)["step"])
        assertEquals("Indiquez la surface du terrain.", errors(result)["area"])
        assertNull(pending()?.propertyType)
    }

    @Test
    fun `a land sale posts without rooms nor parking`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            sale.copy(propertyType = null, area = null),
        )

        val result = mvc.perform(features("propertyType" to "LAND", "area" to "800")).andReturn()

        assertEquals("redirect:/offer-property?step=amenities", result.modelAndView?.viewName)
        assertEquals("LAND", pending()?.propertyType)
        assertNull(pending()?.rooms)
        assertNull(pending()?.parkings)
        assertEquals(emptyList(), pending()?.ownershipDocuments)
    }

    @Test
    fun `a house sale still refuses a missing number of rooms`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            sale.copy(propertyType = null, area = null),
        )

        val result = mvc.perform(features("propertyType" to "HOUSE")).andReturn()

        assertEquals("features", model(result)["step"])
        assertEquals("Indiquez le nombre de chambres.", errors(result)["rooms"])
    }

    @Test
    fun `a land sale keeps any number of documents`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            sale.copy(propertyType = null, area = null),
        )

        val result = mvc.perform(
            features(
                "propertyType" to "LAND",
                "area" to "800",
                "ownershipDocuments" to "LAND_TITLE",
                "ownershipDocuments" to "SURVEY_PLAN",
            ),
        ).andReturn()

        assertEquals("redirect:/offer-property?step=amenities", result.modelAndView?.viewName)
        assertEquals(
            listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
            pending()?.ownershipDocuments,
        )
    }

    @Test
    fun `a villa sale keeps its documents`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            sale.copy(propertyType = null, furnitureType = null),
        )

        mvc.perform(
            features(
                "propertyType" to "HOUSE",
                "furnitureType" to "UNFURNISHED",
                "ownershipDocuments" to "LAND_TITLE",
                "rooms" to "3",
                "hasParking" to "NO",
            ),
        ).andReturn()

        assertEquals(listOf(TitleDocumentType.LAND_TITLE), pending()?.ownershipDocuments)
    }

    @Test
    fun `a house sale's documents reach the mandate`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            sale.copy(
                propertyType = null,
                area = null,
                rooms = null,
                furnitureType = null,
                parkings = null,
            ),
        )
        doReturn(CreateMandateResponse(mandateId = 42)).whenever(mandateClient).create(any())

        mvc.perform(
            features(
                "propertyType" to "HOUSE",
                "furnitureType" to "UNFURNISHED",
                "rooms" to "3",
                "hasParking" to "NO",
                "ownershipDocuments" to "LAND_TITLE",
                "ownershipDocuments" to "SURVEY_PLAN",
            ),
        ).andReturn()
        val result = mvc.perform(post("/offer-property/review").session(session)).andReturn()

        assertEquals("redirect:/offer-property?step=confirmation", result.modelAndView?.viewName)
        val request = argumentCaptor<CreateMandateRequest>()
        verify(mandateClient).create(request.capture())
        assertEquals(
            listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
            request.firstValue.ownershipDocuments,
        )
    }

    @Test
    fun `a rental posts no documents`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            complete.copy(propertyType = null, rooms = null, furnitureType = null, parkings = null),
        )

        mvc.perform(
            features(
                "propertyType" to "APARTMENT",
                "furnitureType" to "UNFURNISHED",
                "rooms" to "2",
                "hasParking" to "NO",
                "ownershipDocuments" to "LAND_TITLE",
            ),
        ).andReturn()

        assertEquals(emptyList(), pending()?.ownershipDocuments)
    }

    @Test
    fun `a rental refuses land`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            complete.copy(
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = null, area = null, rooms = null, furnitureType = null, parkings = null,
            ),
        )

        val result = mvc.perform(features("propertyType" to "LAND", "area" to "800")).andReturn()

        assertEquals("features", model(result)["step"])
        assertEquals("Choisissez le type de bien.", errors(result)["propertyType"])
        assertNull(pending()?.propertyType)
    }

    @Test
    fun `a sale stores no rooms nor furniture`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            sale.copy(propertyType = null, furnitureType = null, rooms = null, parkings = null, area = null),
        )

        mvc.perform(
            features(
                "propertyType" to "LAND",
                "area" to "800",
                "rooms" to "3",
                "furnitureType" to "UNFURNISHED",
                "hasParking" to "NO",
            ),
        ).andReturn()

        assertEquals("LAND", pending()?.propertyType)
        assertNull(pending()?.rooms)
        assertNull(pending()?.furnitureType)
        assertNull(pending()?.parkings)
        assertEquals(800, pending()?.area)
    }

    // The building branch (#274)

    @Test
    fun `a building sale has no amenities step`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, buildingSale.copy(email = null))

        val result = mvc.perform(get("/offer-property?step=amenities").session(session)).andReturn()

        assertEquals("redirect:/offer-property?step=contact", result.modelAndView?.viewName)
        assertEquals(302, result.response.status)
    }

    @Test
    fun `the features step keeps a building's counts`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            buildingSale.copy(propertyType = null, area = null, levels = null, units = null),
        )

        val result = mvc.perform(
            features("propertyType" to "BUILDING", "levels" to "3", "units" to "12", "area" to "850"),
        ).andReturn()

        assertEquals("redirect:/offer-property?step=contact", result.modelAndView?.viewName)
        assertEquals(3, pending()?.levels)
        assertEquals(12, pending()?.units)
        assertNull(pending()?.parkings)
    }

    @Test
    fun `the features step requires a building's counts`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            buildingSale.copy(propertyType = null, area = null, levels = null, units = null),
        )

        val result = mvc.perform(features("propertyType" to "BUILDING", "levels" to "3", "area" to "850")).andReturn()

        assertEquals("features", model(result)["step"])
        assertEquals("Indiquez le nombre d'unités.", errors(result)["units"])
        assertNull(errors(result)["levels"])
        assertNull(pending()?.propertyType)
    }

    @Test
    fun `a rental never offers a building nor keeps its counts`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            complete.copy(
                propertyType = null,
                rooms = null,
                furnitureType = null,
                parkings = null,
            ),
        )

        val result = mvc.perform(
            features("propertyType" to "BUILDING", "levels" to "3", "units" to "12", "hasParking" to "NO"),
        ).andReturn()

        // A building is sold as a whole only (#304): a rental refuses the type and keeps no count.
        assertEquals("features", model(result)["step"])
        assertEquals("Choisissez le type de bien.", errors(result)["propertyType"])
        assertNull(pending()?.levels)
        assertNull(pending()?.units)
    }

    // POST /offer-property/amenities

    @Test
    fun amenitiesRequiresTheRoadAccess() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(roadAccess = null))

        val result = mvc.perform(post("/offer-property/amenities").session(session)).andReturn()

        assertEquals("amenities", model(result)["step"])
        assertEquals("Indiquez comment on accède au bien.", errors(result)["roadAccess"])
    }

    @Test
    fun amenitiesDropsUnknownNames() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(roadAccess = null, amenities = emptySet()))

        val result = mvc.perform(
            post("/offer-property/amenities").session(session)
                .param("roadAccess", "roadPaved")
                .param("amenities", "waterMeter", "teleport", "securityGuard"),
        ).andReturn()

        assertEquals("redirect:/offer-property?step=contact", result.modelAndView?.viewName)
        assertEquals(setOf("waterMeter", "securityGuard"), pending()?.amenities)
        assertEquals("roadPaved", pending()?.roadAccess)
    }

    // POST /offer-property/contact

    @Test
    fun contactNormalizesThePhoneAndEmail() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(email = null))

        val result = mvc.perform(
            post("/offer-property/contact").session(session)
                .param("firstName", " Ray ")
                .param("lastName", "Sponsible")
                .param("email", " Ray@Gmail.COM ")
                .param("whatsappNumber", "+237 6 70 00 00 01")
                .param("preferredContactPeriod", "EVENING"),
        ).andReturn()

        assertEquals("redirect:/offer-property?step=review", result.modelAndView?.viewName)
        assertEquals("Ray", pending()?.firstName)
        assertEquals("ray@gmail.com", pending()?.email)
        assertEquals("+237670000001", pending()?.whatsappNumber)
        assertEquals(ContactPeriodType.EVENING, pending()?.preferredContactPeriod)
    }

    // GET review

    @Test
    fun reviewShowsTheLabels() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/offer-property?step=review").session(session)).andReturn()

        assertEquals("review", model(result)["step"])
        assertEquals("Location, usage résidentiel", model(result)["categoryLabel"])
        assertEquals("Bastos, Yaoundé", model(result)["locationLabel"])
        assertEquals(
            listOf(
                FORMATTER.compactMoney(150_000) + " par mois",
                "Avance de 3 mois",
                "Caution de 2 mois",
                "Disponible dès le " + FORMATTER.date(TODAY.plusDays(10)),
            ),
            model(result)["termsLines"],
        )
        assertTrue((model(result)["termsLines"] as List<*>).none { it.toString().contains("FCFA/m²") })
        assertEquals("Appartement · Non meublé · 3 chambres · Sans parking", model(result)["propertyLabel"])
        assertEquals("Route bitumée, Compteur d'eau", model(result)["amenitiesLabel"])
        assertEquals(
            listOf("Ray Sponsible", "ray@gmail.com", "+237670000001", "Contact le matin"),
            model(result)["contactLines"],
        )
    }

    @Test
    fun reviewShowsNoCautionAndNoAdvance() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            complete.copy(securityDeposit = null, advanceRent = null),
        )

        val result = mvc.perform(get("/offer-property?step=review").session(session)).andReturn()

        assertEquals(
            listOf(
                FORMATTER.compactMoney(150_000) + " par mois",
                "Sans avance de loyer",
                "Sans caution",
                "Disponible dès le " + FORMATTER.date(TODAY.plusDays(10)),
            ),
            model(result)["termsLines"],
        )
    }

    @Test
    fun `a land sale review words the sale and its documents`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            sale.copy(ownershipDocuments = emptyList()),
        )

        val result = mvc.perform(get("/offer-property?step=review").session(session)).andReturn()

        assertEquals("Vente, usage résidentiel", model(result)["categoryLabel"])
        assertEquals(
            listOf(
                FORMATTER.compactMoney(45_000_000),
                "≈ " + FORMATTER.compactMoney(56_300) + "/m² pour " + MandateLabels.formatNumber(800) + " m²",
                "Disponible dès le " + FORMATTER.date(TODAY.plusDays(10)),
            ),
            model(result)["termsLines"],
        )
        assertEquals("Aucun document déclaré", model(result)["ownershipDocumentsLabel"])
    }

    @Test
    fun `a villa sale review has no rate line`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            sale.copy(
                propertyType = "HOUSE",
                area = null,
                rooms = "3",
                parkings = "0",
                furnitureType = "UNFURNISHED",
            ),
        )

        val result = mvc.perform(get("/offer-property?step=review").session(session)).andReturn()

        assertTrue((model(result)["termsLines"] as List<*>).none { it.toString().contains("FCFA/m²") })
        assertTrue((model(result)["termsLines"] as List<*>).none { it.toString().contains("/unité") })
        assertNull(model(result)["pricePerUnitLabel"])
    }

    @Test
    fun `a villa sale review lists its documents, or none`() {
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            sale.copy(
                propertyType = "HOUSE",
                area = null,
                rooms = "3",
                parkings = "0",
                furnitureType = "UNFURNISHED",
            ),
        )

        val result = mvc.perform(get("/offer-property?step=review").session(session)).andReturn()

        assertEquals("review", model(result)["step"])
        assertEquals("Aucun document déclaré", model(result)["ownershipDocumentsLabel"])
        assertEquals("Vente, usage résidentiel", model(result)["categoryLabel"])
    }

    @Test
    fun `a building sale's review reads its counts once and its rate`() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, buildingSale)

        val result = mvc.perform(get("/offer-property?step=review").session(session)).andReturn()

        assertEquals("Immeuble entier · 3 étages · 12 unités · 850 m²", model(result)["propertyLabel"])
        assertEquals("≈ " + FORMATTER.compactMoney(20_833_300) + "/unité pour 12 unités", model(result)["pricePerUnitLabel"])
        assertEquals(
            listOf(
                FORMATTER.compactMoney(250_000_000),
                "≈ " + FORMATTER.compactMoney(20_833_300) + "/unité pour 12 unités",
                "Disponible dès le " + FORMATTER.date(TODAY.plusDays(10)),
            ),
            model(result)["termsLines"],
        )
        assertNull(model(result)["levelsLabel"])
        assertNull(model(result)["unitsLabel"])
    }

    // POST /offer-property/review

    @Test
    fun submitSendsAListing() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)
        doReturn(CreateMandateResponse(mandateId = 42)).whenever(mandateClient).create(any())

        val result = mvc.perform(post("/offer-property/review").session(session)).andReturn()

        assertEquals("redirect:/offer-property?step=confirmation", result.modelAndView?.viewName)
        val request = argumentCaptor<CreateMandateRequest>()
        verify(mandateClient).create(request.capture())
        assertEquals(MandateType.LISTING, request.firstValue.mandateType)
        assertEquals(complete.availableFrom, request.firstValue.availableFrom)
        assertEquals(TENANT.currencyCode, request.firstValue.price.currency)
        assertNull(pending())
        val submitted = session.getAttribute(OfferPropertyController.SUBMITTED_ATTRIBUTE) as SubmittedMandate
        assertEquals("ray@gmail.com", submitted.email)

        val confirmation = mvc.perform(get("/offer-property?step=confirmation").session(session)).andReturn()
        assertEquals("confirmation", model(confirmation)["step"])
        assertEquals(LoginController.maskEmail("ray@gmail.com"), model(confirmation)["maskedEmail"])
        assertEquals("+237670000001", model(confirmation)["whatsappNumber"])
        assertEquals("le matin", model(confirmation)["contactPeriodLabel"])
    }

    @Test
    fun `submit a sale - its project and its exit link`() {
        doReturn(TENANT.copy(publicPortalUrl = "https://www.ndopify.com/")).whenever(tenantContext).getTenant()
        session.setAttribute(
            PendingListing.SESSION_ATTRIBUTE,
            sale.copy(ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE)),
        )
        doReturn(CreateMandateResponse(mandateId = 42)).whenever(mandateClient).create(any())

        val result = mvc.perform(post("/offer-property/review").session(session)).andReturn()

        assertEquals("redirect:/offer-property?step=confirmation", result.modelAndView?.viewName)
        val request = argumentCaptor<CreateMandateRequest>()
        verify(mandateClient).create(request.capture())
        assertEquals(ListingType.SALE, request.firstValue.listingType)
        assertEquals(listOf(TitleDocumentType.LAND_TITLE), request.firstValue.ownershipDocuments)

        val confirmation = mvc.perform(get("/offer-property?step=confirmation").session(session)).andReturn()
        assertEquals(ListingType.SALE, model(confirmation)["listingType"])
        assertEquals("https://www.ndopify.com/owner/sell", model(confirmation)["exitUrl"])
    }

    @Test
    fun submitWhileIncompleteRedirects() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(email = null))

        val result = mvc.perform(post("/offer-property/review").session(session)).andReturn()

        assertEquals("redirect:/offer-property?step=contact", result.modelAndView?.viewName)
        verify(mandateClient, never()).create(any())
    }

    @Test
    fun submitFailureShowsTheError() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).create(any())

        val server = mvc.perform(post("/offer-property/review").session(session)).andReturn()
        assertEquals("review", model(server)["step"])
        assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", model(server)["error"])
        assertEquals(complete, pending())
        assertNull(session.getAttribute(OfferPropertyController.SUBMITTED_ATTRIBUTE))

        doThrow(ResourceAccessException("Connection refused")).whenever(mandateClient).create(any())
        val unreachable = mvc.perform(post("/offer-property/review").session(session)).andReturn()
        assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", model(unreachable)["error"])
    }

    @Test
    fun confirmationWithoutSubmissionShowsStep1() {
        val result = mvc.perform(get("/offer-property?step=confirmation").session(session)).andReturn()

        assertEquals("property", model(result)["step"])
    }

    // Helpers

    private fun submitted() = SubmittedMandate(
        email = "ray@gmail.com",
        whatsappNumber = "+237670000001",
        preferredContactPeriod = ContactPeriodType.MORNING,
    )

    private fun pending(): PendingListing? = session.getAttribute(PendingListing.SESSION_ATTRIBUTE) as PendingListing?

    @Suppress("UNCHECKED_CAST")
    private fun errors(result: MvcResult): Map<String, String> = model(result)["errors"] as Map<String, String>

    private fun model(result: MvcResult): Map<String, Any?> = result.modelAndView!!.model.also {
        assertTrue(result.response.status < 400, "HTTP ${result.response.status}")
    }
}
