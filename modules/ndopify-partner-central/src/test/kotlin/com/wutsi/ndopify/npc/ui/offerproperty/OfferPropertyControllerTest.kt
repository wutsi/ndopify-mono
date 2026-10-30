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
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.npc.ui.findproperty.SubmittedMandate
import com.wutsi.ndopify.npc.ui.login.LoginController
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
            listOf("APARTMENT", "HOUSE", "ROOM", "STUDIO", "DUPLEX", "BUILDING"),
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
            post("/offer-property/property").session(session).param("propertyCategory", "RESIDENTIAL"),
        ).andReturn()

        assertEquals("redirect:/offer-property?step=address", result.modelAndView?.viewName)
        assertEquals(PropertyCategory.RESIDENTIAL, pending()?.propertyCategory)
    }

    @Test
    fun propertyWithoutCategory() {
        val result = mvc.perform(post("/offer-property/property").session(session)).andReturn()

        assertEquals("property", model(result)["step"])
        assertEquals("Indiquez si le bien est pour y habiter ou pour une activité.", errors(result)["propertyCategory"])
        assertNull(pending())
    }

    // POST /offer-property/address

    @Test
    fun addressSaves() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, PendingListing(propertyCategory = PropertyCategory.RESIDENTIAL))

        val result = mvc.perform(
            post("/offer-property/address").session(session)
                .param("cityId", "$YAOUNDE").param("neighborhoodId", "1001"),
        ).andReturn()

        assertEquals("redirect:/offer-property?step=terms", result.modelAndView?.viewName)
        assertEquals(1001L, pending()?.neighborhoodId)
    }

    @Test
    fun addressRejectsANeighborhoodOfAnotherCity() {
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, PendingListing(propertyCategory = PropertyCategory.RESIDENTIAL))

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
        session.setAttribute(PendingListing.SESSION_ATTRIBUTE, PendingListing(propertyCategory = PropertyCategory.RESIDENTIAL))

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

        assertEquals("redirect:/offer-property", result.modelAndView?.viewName)
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

        assertEquals("Indiquez un loyer d'au moins ${FORMATTER.money(5000)} par mois.", errors(result)["priceAmount"])
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
        assertNull(pending()?.rooms)
        assertNull(pending()?.parkings)
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
                FORMATTER.money(150_000) + " par mois",
                "Avance de 3 mois",
                "Caution de 2 mois",
                "Disponible dès le " + FORMATTER.date(TODAY.plusDays(10)),
            ),
            model(result)["termsLines"],
        )
        assertEquals("Appartement · Non meublé · 3 chambres · Sans parking", model(result)["propertyLabel"])
        assertEquals("Route bitumée, Compteur d'eau", model(result)["amenitiesLabel"])
        assertEquals(
            listOf("Ray Sponsible", "ray@gmail.com", "+237670000001", "Contact le matin"),
            model(result)["contactLines"],
        )
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
