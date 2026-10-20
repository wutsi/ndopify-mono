package com.wutsi.ndopify.npc.ui.findproperty

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindPropertyControllerTest {
    companion object {
        const val YAOUNDE = 2220957L
        const val DOUALA = 2232593L
        const val MONTREAL = 6077243L
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
        Location(id = 1002, parentId = YAOUNDE, name = "Essos", type = LocationType.NEIGHBORHOOD, country = "CM"),
        Location(id = 1003, parentId = YAOUNDE, name = "Mendong", type = LocationType.NEIGHBORHOOD, country = "CM"),
        Location(id = 1004, parentId = YAOUNDE, name = "Odza", type = LocationType.NEIGHBORHOOD, country = "CM"),
        Location(id = 2001, parentId = DOUALA, name = "Akwa", type = LocationType.NEIGHBORHOOD, country = "CM"),
    )

    private val complete = PendingMandateTest.COMPLETE

    @BeforeEach
    fun setUp() {
        doReturn(TENANT).whenever(tenantContext).getTenant()
        whenever(locationClient.search(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<SearchLocationRequest>(0)
            SearchLocationResponse(locations = locations.filter { request.ids.contains(it.id) })
        }

        val controller = FindPropertyController(mandateClient, locationClient, tenantContext)
        // The view is named after its URL ("find-property" at /find-property); the default resolver would loop.
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
        session = MockHttpSession()
    }

    // GET /find-property

    @Test
    fun show() {
        val result = mvc.perform(get("/find-property").session(session)).andReturn()

        assertEquals("find-property", result.modelAndView?.viewName)
        assertEquals("need", model(result)["step"])
        assertEquals(1, model(result)["stepNumber"])
        assertEquals(7, model(result)["stepCount"])
        assertEquals(PendingMandate(), model(result)["answers"])
        assertEquals(emptyMap<String, String>(), model(result)["errors"])
        assertEquals(false, model(result)["returning"])
    }

    @Test
    fun showNeverSkipsAnIncompleteStep() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(priceAmount = null))

        val result = mvc.perform(get("/find-property?step=contact").session(session)).andReturn()

        assertEquals("budget", model(result)["step"])
    }

    @Test
    fun showUnknownStep() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/find-property?step=nimportequoi").session(session)).andReturn()

        assertEquals("need", model(result)["step"])
    }

    @Test
    fun showArea() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/find-property?step=area").session(session)).andReturn()

        assertEquals("area", model(result)["step"])
        assertEquals("Yaoundé", (model(result)["city"] as Location).name)
        assertEquals(listOf("Bastos", "Essos"), names(model(result)["neighborhoods"]))
    }

    @Test
    fun propertyTypesFilteredByCategory() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(propertyCategory = PropertyCategory.COMMERCIAL)
        )

        val result = mvc.perform(get("/find-property?step=property").session(session)).andReturn()

        assertEquals("property", model(result)["step"])
        assertEquals(
            listOf(
                PropertyType.RETAIL,
                PropertyType.OFFICE,
                PropertyType.INDUSTRIAL,
                PropertyType.BUILDING,
                PropertyType.LAND
            ),
            model(result)["propertyTypes"],
        )
    }

    @Test
    fun returningOnlyOnceEverythingIsAnswered() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/find-property?step=budget&returnTo=review").session(session)).andReturn()

        assertEquals(true, model(result)["returning"])
    }

    @Test
    fun reviewLabels() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(get("/find-property?step=review").session(session)).andReturn()

        assertEquals("review", model(result)["step"])
        assertEquals("Location, usage résidentiel", model(result)["categoryLabel"])
        assertEquals(
            "Appartement · 3 chambres · Meublé ou non · 1 place de parking · 80 m² minimum",
            model(result)["propertyLabel"],
        )
        assertEquals("150\u00a0000\u00a0FCFA par mois", model(result)["priceLabel"])
        assertEquals("Dans moins d'1 mois", model(result)["movingPeriodLabel"])
        assertEquals("le soir", model(result)["contactPeriodLabel"])
        assertEquals(listOf("Compteur d'eau", "Gardien"), model(result)["amenityLabels"])
        assertEquals(listOf("Bastos", "Essos"), names(model(result)["neighborhoods"]))
    }

    @Test
    fun reviewLabelForAnyType() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(
                propertyType = "ANY",
                rooms = "2",
                parkings = null,
                area = null,
                furnitureType = "UNFURNISHED"
            ),
        )

        val result = mvc.perform(get("/find-property?step=review").session(session)).andReturn()

        assertEquals("Tout type de bien · 2 chambres · Non meublé", model(result)["propertyLabel"])
    }

    // POST /find-property/need

    @Test
    fun need() {
        val result = mvc.perform(
            post("/find-property/need").session(session).param("propertyCategory", "RESIDENTIAL"),
        ).andReturn()

        assertEquals("redirect:/find-property?step=area", result.modelAndView?.viewName)
        assertEquals(PropertyCategory.RESIDENTIAL, pending()?.propertyCategory)
    }

    @Test
    fun needMissing() {
        val result = mvc.perform(post("/find-property/need").session(session)).andReturn()

        assertEquals("need", model(result)["step"])
        assertEquals("Indiquez si le bien est pour y habiter ou pour une activité.", errors(result)["propertyCategory"])
        assertNull(pending())
    }

    @Test
    fun needClearsAnOldConfirmation() {
        session.setAttribute(
            SubmittedMandate.SESSION_ATTRIBUTE,
            SubmittedMandate("ray@gmail.com", "+237670000001", null)
        )

        mvc.perform(post("/find-property/need").session(session).param("propertyCategory", "COMMERCIAL")).andReturn()

        assertNull(session.getAttribute(SubmittedMandate.SESSION_ATTRIBUTE))
    }

    // POST /find-property/area

    @Test
    fun area() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            PendingMandate(propertyCategory = PropertyCategory.RESIDENTIAL)
        )

        val result = mvc.perform(
            post("/find-property/area").session(session)
                .param("cityId", "$YAOUNDE")
                .param("neighborhoodIds", "1001", "1002", "1001"),
        ).andReturn()

        assertEquals("redirect:/find-property?step=property", result.modelAndView?.viewName)
        assertEquals(YAOUNDE, pending()?.cityId)
        assertEquals(listOf(1001L, 1002L), pending()?.neighborhoodIds)
    }

    @Test
    fun areaWithoutNeighborhoods() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            PendingMandate(propertyCategory = PropertyCategory.RESIDENTIAL)
        )

        val result = mvc.perform(post("/find-property/area").session(session).param("cityId", "$YAOUNDE")).andReturn()

        assertEquals("redirect:/find-property?step=property", result.modelAndView?.viewName)
        assertEquals(YAOUNDE, pending()?.cityId)
        assertEquals(emptyList(), pending()?.neighborhoodIds)
    }

    @Test
    fun areaBeforeNeedRedirects() {
        val result = mvc.perform(
            post("/find-property/area").session(session).param("cityId", "$YAOUNDE").param("neighborhoodIds", "1001"),
        ).andReturn()

        assertEquals("redirect:/find-property", result.modelAndView?.viewName)
        assertNull(pending())
    }

    @Test
    fun areaRejectsForeignCity() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            PendingMandate(propertyCategory = PropertyCategory.RESIDENTIAL)
        )

        val result = mvc.perform(
            post("/find-property/area").session(session).param("cityId", "$MONTREAL").param("neighborhoodIds", "1001"),
        ).andReturn()

        assertEquals("area", model(result)["step"])
        assertEquals("Cette ville n'est pas proposée. Choisissez-en une dans la liste.", errors(result)["cityId"])
        assertNull(pending()?.cityId)
    }

    @Test
    fun areaRejectsNeighbourhoodOfOtherCity() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            PendingMandate(propertyCategory = PropertyCategory.RESIDENTIAL)
        )

        val result = mvc.perform(
            post("/find-property/area").session(session).param("cityId", "$YAOUNDE")
                .param("neighborhoodIds", "1001", "2001"),
        ).andReturn()

        assertEquals("Choisissez des quartiers de la ville sélectionnée.", errors(result)["neighborhoodIds"])
    }

    @Test
    fun areaRejectsTooManyNeighbourhoods() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            PendingMandate(propertyCategory = PropertyCategory.RESIDENTIAL)
        )

        val result = mvc.perform(
            post("/find-property/area").session(session)
                .param("cityId", "$YAOUNDE")
                .param("neighborhoodIds", "1001", "1002", "1003", "1004"),
        ).andReturn()

        assertEquals("Choisissez au plus 3 quartiers.", errors(result)["neighborhoodIds"])
        assertEquals(3, (model(result)["neighborhoods"] as List<*>).size)
    }

    @Test
    fun areaMissingCity() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            PendingMandate(propertyCategory = PropertyCategory.RESIDENTIAL)
        )

        val result = mvc.perform(post("/find-property/area").session(session)).andReturn()

        assertEquals("Choisissez la ville.", errors(result)["cityId"])
        assertNull(errors(result)["neighborhoodIds"])
    }

    // POST /find-property/property

    @Test
    fun property() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null))

        val result = mvc.perform(
            post("/find-property/property").session(session)
                .param("propertyType", "HOUSE")
                .param("rooms", "4")
                .param("furnitureType", "UNFURNISHED")
                .param("parkings", "2")
                .param("area", ""),
        ).andReturn()

        assertEquals("redirect:/find-property?step=amenities", result.modelAndView?.viewName)
        assertEquals("HOUSE", pending()?.propertyType)
        assertEquals("4", pending()?.rooms)
        assertEquals("UNFURNISHED", pending()?.furnitureType)
        assertEquals("2", pending()?.parkings)
        assertNull(pending()?.area)
    }

    @Test
    fun propertyRejectsTypeOfOtherCategory() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null))

        val result = mvc.perform(
            post("/find-property/property").session(session).param("propertyType", "OFFICE")
                .param("furnitureType", "ANY"),
        ).andReturn()

        assertEquals("find-property", result.modelAndView?.viewName)
        assertEquals("property", model(result)["step"])
        assertEquals("Choisissez un type de bien proposé.", errors(result)["propertyType"])
        assertNull(pending()?.propertyType)
    }

    @Test
    fun propertyRejectsUnknownRooms() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null))

        val result = mvc.perform(
            post("/find-property/property").session(session)
                .param("propertyType", "APARTMENT")
                .param("rooms", "7")
                .param("furnitureType", "ANY"),
        ).andReturn()

        assertEquals("Indiquez le nombre de chambres.", errors(result)["rooms"])
    }

    @Test
    fun propertyStudioIgnoresRooms() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null))

        val result = mvc.perform(
            post("/find-property/property").session(session)
                .param("propertyType", "STUDIO")
                .param("rooms", "3")
                .param("furnitureType", "ANY"),
        ).andReturn()

        assertEquals("redirect:/find-property?step=amenities", result.modelAndView?.viewName)
        assertNull(pending()?.rooms)
    }

    @Test
    fun propertyLandIgnoresFurnitureAndParkings() {
        session.setAttribute(
            PendingMandate.SESSION_ATTRIBUTE,
            complete.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = null, rooms = null),
        )

        mvc.perform(
            post("/find-property/property").session(session)
                .param("propertyType", "LAND")
                .param("furnitureType", "FULLY_FURNISHED")
                .param("parkings", "2"),
        ).andReturn()

        assertEquals("LAND", pending()?.propertyType)
        assertNull(pending()?.furnitureType)
        assertNull(pending()?.parkings)
    }

    @Test
    fun propertyMissingFurniture() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null))

        val result = mvc.perform(post("/find-property/property").session(session)).andReturn()

        assertEquals("Indiquez si le bien doit être meublé.", errors(result)["furnitureType"])
        assertEquals("Indiquez le nombre de chambres.", errors(result)["rooms"])
        assertNull(errors(result)["propertyType"])
    }

    @Test
    fun propertyAreaWithSeparators() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null))

        mvc.perform(
            post("/find-property/property").session(session)
                .param("propertyType", "STUDIO")
                .param("furnitureType", "ANY")
                .param("area", "1 200"),
        ).andReturn()

        assertEquals(1200, pending()?.area)
    }

    @Test
    fun propertyAreaNotANumber() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null))

        val result = mvc.perform(
            post("/find-property/property").session(session)
                .param("propertyType", "STUDIO")
                .param("furnitureType", "ANY")
                .param("area", "abc"),
        ).andReturn()

        assertEquals("Indiquez une surface d'au moins 5 m², ou laissez ce champ vide.", errors(result)["area"])
    }

    @Test
    fun propertyAreaTooLarge() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null))

        val result = mvc.perform(
            post("/find-property/property").session(session)
                .param("propertyType", "STUDIO")
                .param("furnitureType", "ANY")
                .param("area", "99999999999"),
        ).andReturn()

        assertEquals("Indiquez une surface d'au moins 5 m², ou laissez ce champ vide.", errors(result)["area"])
    }

    @Test
    fun propertyRejectsUnknownParkings() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null))

        val result = mvc.perform(
            post("/find-property/property").session(session)
                .param("propertyType", "STUDIO")
                .param("furnitureType", "ANY")
                .param("parkings", "9"),
        ).andReturn()

        assertEquals("Choisissez un nombre de places proposé.", errors(result)["parkings"])
    }

    // POST /find-property/amenities

    @Test
    fun amenitiesDropsUnknownNames() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(
            post("/find-property/amenities").session(session).param("amenities", "waterMeter", "pool"),
        ).andReturn()

        assertEquals("redirect:/find-property?step=budget", result.modelAndView?.viewName)
        assertEquals(setOf("waterMeter"), pending()?.amenities)
    }

    @Test
    fun amenitiesNoneChecked() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(priceAmount = null))

        val result = mvc.perform(post("/find-property/amenities").session(session)).andReturn()

        assertEquals("redirect:/find-property?step=budget", result.modelAndView?.viewName)
        assertEquals(emptySet(), pending()?.amenities)
    }

    @Test
    fun amenitiesBeforePropertyRedirects() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(furnitureType = null))

        val result = mvc.perform(post("/find-property/amenities").session(session)).andReturn()

        assertEquals("redirect:/find-property?step=property", result.modelAndView?.viewName)
    }

    // POST /find-property/budget

    @Test
    fun budget() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(priceAmount = null, movingPeriod = null))

        val result = mvc.perform(
            post("/find-property/budget").session(session)
                .param("priceAmount", "150 000")
                .param("movingPeriod", "WITHIN_3_MONTHS"),
        ).andReturn()

        assertEquals("redirect:/find-property?step=contact", result.modelAndView?.viewName)
        assertEquals(150_000L, pending()?.priceAmount)
        assertEquals(MovingPeriod.WITHIN_3_MONTHS, pending()?.movingPeriod)
    }

    @Test
    fun budgetBelowMinimum() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(priceAmount = null))

        val result = mvc.perform(
            post("/find-property/budget").session(session)
                .param("priceAmount", "4 999")
                .param("movingPeriod", "WITHIN_1_MONTH"),
        ).andReturn()

        assertEquals("Indiquez un loyer d'au moins 5\u00a0000\u00a0FCFA par mois.", errors(result)["priceAmount"])
        assertNull(pending()?.priceAmount)
    }

    @Test
    fun budgetMissingMovingPeriod() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(movingPeriod = null))

        val result =
            mvc.perform(post("/find-property/budget").session(session).param("priceAmount", "50000")).andReturn()

        assertEquals("Indiquez quand vous souhaitez emménager.", errors(result)["movingPeriod"])
    }

    // POST /find-property/contact

    @Test
    fun contact() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(firstName = null))

        val result = mvc.perform(
            post("/find-property/contact").session(session)
                .param("firstName", " Ray ")
                .param("lastName", "Sponsible")
                .param("email", "Ray@Gmail.com ")
                .param("whatsappNumber", "00237 670 00 00 01")
                .param("preferredContactPeriod", "MORNING")
                .param("comments", "  Près du marché  "),
        ).andReturn()

        assertEquals("redirect:/find-property?step=review", result.modelAndView?.viewName)
        assertEquals("Ray", pending()?.firstName)
        assertEquals("ray@gmail.com", pending()?.email)
        assertEquals("+237670000001", pending()?.whatsappNumber)
        assertEquals(ContactPeriodType.MORNING, pending()?.preferredContactPeriod)
        assertEquals("Près du marché", pending()?.comments)
    }

    @Test
    fun contactErrors() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(firstName = null))

        val result = mvc.perform(
            post("/find-property/contact").session(session).param("email", "ray@").param("whatsappNumber", "123"),
        ).andReturn()

        assertEquals("contact", model(result)["step"])
        assertEquals("Saisissez votre prénom.", errors(result)["firstName"])
        assertEquals("Saisissez votre nom.", errors(result)["lastName"])
        assertEquals("Cette adresse email semble incomplète. Exemple : nom@exemple.com", errors(result)["email"])
        assertEquals(
            "Ce numéro n'est pas un numéro mobile valide. Exemple : 6 77 12 34 56",
            errors(result)["whatsappNumber"]
        )
        assertEquals("Indiquez quand votre agent peut vous contacter.", errors(result)["preferredContactPeriod"])
    }

    @Test
    fun contactCommentsTooLong() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(
            post("/find-property/contact").session(session)
                .param("firstName", "Ray")
                .param("lastName", "Sponsible")
                .param("email", "ray@gmail.com")
                .param("whatsappNumber", "+237670000001")
                .param("preferredContactPeriod", "MORNING")
                .param("comments", "a".repeat(501)),
        ).andReturn()

        assertEquals("Votre message ne doit pas dépasser 500 caractères.", errors(result)["comments"])
    }

    @Test
    fun returnToReview() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

        val result = mvc.perform(
            post("/find-property/budget").session(session)
                .param("returnTo", "review")
                .param("priceAmount", "200000")
                .param("movingPeriod", "AFTER_3_MONTHS"),
        ).andReturn()

        assertEquals("redirect:/find-property?step=review", result.modelAndView?.viewName)
    }

    // POST /find-property/review

    @Test
    fun submit() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)
        doReturn(CreateMandateResponse(mandateId = 42)).whenever(mandateClient).create(any())

        val result = mvc.perform(post("/find-property/review").session(session)).andReturn()

        assertEquals("redirect:/find-property?step=confirmation", result.modelAndView?.viewName)
        val request = argumentCaptor<CreateMandateRequest>()
        verify(mandateClient).create(request.capture())
        assertEquals(MandateType.SEARCH, request.firstValue.mandateType)
        assertEquals("XAF", request.firstValue.price.currency)
        assertEquals(listOf(1001L, 1002L), request.firstValue.neighbourhoodIds)
        assertNull(pending())
        val submitted = session.getAttribute(SubmittedMandate.SESSION_ATTRIBUTE) as SubmittedMandate
        assertEquals("ray@gmail.com", submitted.email)
        assertEquals("+237670000001", submitted.whatsappNumber)
        assertEquals(ContactPeriodType.EVENING, submitted.preferredContactPeriod)
    }

    @Test
    fun submitIncompleteRedirects() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(email = null))

        val result = mvc.perform(post("/find-property/review").session(session)).andReturn()

        assertEquals("redirect:/find-property?step=contact", result.modelAndView?.viewName)
        verify(mandateClient, never()).create(any())
    }

    @Test
    fun submitServerError() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).create(any())

        val result = mvc.perform(post("/find-property/review").session(session)).andReturn()

        assertEquals("review", model(result)["step"])
        assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", model(result)["error"])
        assertEquals(complete, pending())
        assertNull(session.getAttribute(SubmittedMandate.SESSION_ATTRIBUTE))
    }

    @Test
    fun submitServerUnreachable() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)
        doThrow(ResourceAccessException("Connection refused")).whenever(mandateClient).create(any())

        val result = mvc.perform(post("/find-property/review").session(session)).andReturn()

        assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", model(result)["error"])
        assertEquals(complete, pending())
    }

    @Test
    fun submitServerDown() {
        // ndopify-server is down: the location names of the review can't be loaded either.
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)
        doThrow(ResourceAccessException("Connection refused")).whenever(mandateClient).create(any())
        doThrow(ResourceAccessException("Connection refused")).whenever(locationClient).search(any())

        val result = mvc.perform(post("/find-property/review").session(session)).andReturn()

        assertEquals("review", model(result)["step"])
        assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", model(result)["error"])
        assertNull(model(result)["city"])
        assertEquals(emptyList<Location>(), model(result)["neighborhoods"])
        assertEquals(complete, pending())
    }

    @Test
    fun submitTwiceCreatesOneMandate() {
        session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)
        doReturn(CreateMandateResponse(mandateId = 42)).whenever(mandateClient).create(any())

        mvc.perform(post("/find-property/review").session(session)).andReturn()
        val result = mvc.perform(post("/find-property/review").session(session)).andReturn()

        assertEquals("redirect:/find-property", result.modelAndView?.viewName)
        verify(mandateClient, times(1)).create(any())
    }

    // GET /find-property?step=confirmation

    @Test
    fun confirmation() {
        session.setAttribute(
            SubmittedMandate.SESSION_ATTRIBUTE,
            SubmittedMandate("ray@gmail.com", "+237670000001", ContactPeriodType.EVENING),
        )

        val result = mvc.perform(get("/find-property?step=confirmation").session(session)).andReturn()

        assertEquals("confirmation", model(result)["step"])
        assertEquals("ra•••@gmail.com", model(result)["maskedEmail"])
        assertEquals("+237670000001", model(result)["whatsappNumber"])
        assertEquals("le soir", model(result)["contactPeriodLabel"])
    }

    @Test
    fun confirmationWithoutSubmissionShowsWizard() {
        val result = mvc.perform(get("/find-property?step=confirmation").session(session)).andReturn()

        assertEquals("need", model(result)["step"])
    }

    private fun pending(): PendingMandate? = session.getAttribute(PendingMandate.SESSION_ATTRIBUTE) as PendingMandate?

    @Suppress("UNCHECKED_CAST")
    private fun errors(result: MvcResult): Map<String, String> = model(result)["errors"] as Map<String, String>

    @Suppress("UNCHECKED_CAST")
    private fun names(locations: Any?): List<String> = (locations as List<Location>).map { it.name }

    private fun model(result: MvcResult): Map<String, Any?> = result.modelAndView!!.model.also {
        assertTrue(result.response.status < 400, "HTTP ${result.response.status}")
    }
}
