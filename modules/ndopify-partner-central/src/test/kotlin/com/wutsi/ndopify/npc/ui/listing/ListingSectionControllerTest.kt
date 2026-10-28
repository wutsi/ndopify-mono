package com.wutsi.ndopify.npc.ui.listing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.UpdateListingRequest
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.LISTING
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AKWA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BASTOS
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingSectionControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val listingClient = mock<NdListingClient>()
    private val mandateClient = mock<NdMandateClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        ListingFixtures.mockNeighborhoods(locationClient)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(LISTING).whenever(listingClient).get(501)
        doReturn(MANDATE).whenever(mandateClient).get(203)

        val controller = ListingSectionController(
            currentAgent,
            listingClient,
            mandateClient,
            locationClient,
            tenantContext,
            clock,
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HttpClientErrorHandler(mock()))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    @Suppress("UNCHECKED_CAST")
    private fun errors(result: MvcResult) = model(result)["errors"] as Map<String, String>

    private fun answers(result: MvcResult) = model(result)["answers"] as ListingSectionForm

    private fun request(): UpdateListingRequest {
        val captor = argumentCaptor<UpdateListingRequest>()
        verify(listingClient).update(any(), captor.capture())
        return captor.firstValue
    }

    private fun conflict(code: String): HttpClientErrorException {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        return ex
    }

    private fun location() = post("/listings/501/location")
        .param("version", "2")
        .param("neighborhoodId", AKWA.toString())
        .param("landmark", "Face au marché")

    @Test
    fun show() {
        val result = mvc.perform(get("/listings/501/property")).andReturn()

        assertEquals("listing/section", result.modelAndView?.viewName)
        val view = model(result)["section"] as SectionView
        assertEquals("property", view.slug)
        assertTrue(view.editable)
        assertEquals("APARTMENT", answers(result).propertyType)
        assertEquals(2L, answers(result).version)
        assertEquals(emptyMap<String, String>(), errors(result))
        assertEquals("Indiqué par le propriétaire : 3", (model(result)["ownerHints"] as Map<*, *>)["rooms"])
        assertEquals("FCFA", model(result)["currency"])
        assertEquals("SN", model(result)["initials"])
    }

    @Test
    fun `show - location has the city's neighbourhoods`() {
        val result = mvc.perform(get("/listings/501/location")).andReturn()

        assertEquals("Douala", model(result)["city"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("Akwa", "Bonapriso"), (model(result)["neighborhoods"] as List<Location>).map { it.name })
    }

    @Test
    fun `show - location keeps the listing's neighbourhood past the city's first ones`() {
        // The city search is capped: the listing's (and the landlord's) neighbourhood may not be in it
        whenever(locationClient.search(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<SearchLocationRequest>(0)
            val locations = if (request.parentId != null) {
                ProfileFixtures.LOCATIONS.filter { it.id == AKWA }
            } else {
                ProfileFixtures.LOCATIONS.filter { request.ids.contains(it.id) }
            }
            SearchLocationResponse(locations = locations)
        }

        val result = mvc.perform(get("/listings/501/location")).andReturn()

        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("Akwa", "Bonapriso"), (model(result)["neighborhoods"] as List<Location>).map { it.name })
    }

    @Test
    fun `show - terms can't be later than in 12 months`() {
        val result = mvc.perform(get("/listings/501/terms")).andReturn()

        assertEquals("2026-10-26", model(result)["today"])
        assertEquals("2027-10-26", model(result)["maxDate"])
    }

    @Test
    fun `show - sent is read-only`() {
        doReturn(LISTING.copy(status = ListingStatus.UNDER_VERIFICATION)).whenever(listingClient).get(501)

        val view = model(mvc.perform(get("/listings/501/gps")).andReturn())["section"] as SectionView

        assertFalse(view.editable)
    }

    @Test
    fun `show - mandate unavailable`() {
        doThrow(ResourceAccessException("down")).whenever(mandateClient).get(203)

        val result = mvc.perform(get("/listings/501/property")).andReturn()

        assertEquals("listing/section", result.modelAndView?.viewName)
        assertEquals(emptyMap<String, String>(), model(result)["ownerHints"])
    }

    @Test
    fun `show - unknown section`() {
        val result = mvc.perform(get("/listings/501/garden")).andReturn()

        assertEquals(404, result.response.status)
        verify(listingClient, never()).get(any())
    }

    @Test
    fun `show - another agent's listing is locked`() {
        doReturn(LISTING.copy(agentId = 999)).whenever(listingClient).get(501)

        val result = mvc.perform(get("/listings/501/gps")).andReturn()

        assertEquals("listing/section", result.modelAndView?.viewName)
        assertEquals(true, model(result)["locked"])
        assertNull(model(result)["section"])
    }

    @Test
    fun `show - malformed id is locked`() {
        assertEquals(true, model(mvc.perform(get("/listings/abc/gps")).andReturn())["locked"])
    }

    @Test
    fun `show - server error is unavailable`() {
        doThrow(HttpServerErrorException(HttpStatus.BAD_GATEWAY)).whenever(listingClient).get(501)

        val result = mvc.perform(get("/listings/501/gps")).andReturn()

        assertEquals(true, model(result)["unavailable"])
        assertNull(model(result)["locked"])
    }

    @Test
    fun save() {
        val result = mvc.perform(location()).andReturn()

        val request = request()
        assertEquals(2L, request.version)
        assertEquals(ListingSection.LOCATION, request.section)
        assertEquals(AKWA, request.neighborhoodId)
        assertEquals("Face au marché", request.landmark)
        assertEquals("redirect:/listings/501", result.modelAndView?.viewName)
        assertEquals("saved-location", result.flashMap[ListingController.OUTCOME])
    }

    @Test
    fun `save - amenities`() {
        mvc.perform(
            post("/listings/501/amenities")
                .param("version", "2")
                .param("roadAccess", "trackAccessibleByCar")
                .param("waterWell", "true"),
        ).andReturn()

        val request = request()
        assertEquals(true, request.trackAccessibleByCar)
        assertEquals(false, request.roadPaved)
        assertEquals(true, request.waterWell)
        assertEquals(false, request.waterMeter)
    }

    @Test
    fun `save - invalid`() {
        val result = mvc.perform(post("/listings/501/location").param("version", "2").param("landmark", "École"))
            .andReturn()

        verify(listingClient, never()).update(any(), any())
        assertEquals("listing/section", result.modelAndView?.viewName)
        assertEquals(mapOf("neighborhoodId" to "Choisissez le quartier du bien."), errors(result))
        assertEquals("École", answers(result).landmark)
    }

    @Test
    fun `save - neighbourhood of another city`() {
        val result = mvc.perform(location().param("neighborhoodId", BASTOS.toString())).andReturn()

        verify(listingClient, never()).update(any(), any())
        assertEquals(mapOf("neighborhoodId" to "Choisissez le quartier du bien."), errors(result))
    }

    @Test
    fun `save - changed meanwhile`() {
        doThrow(conflict(ErrorCode.LISTING_VERSION_CONFLICT)).whenever(listingClient).update(any(), any())
        doReturn(LISTING).doReturn(LISTING.copy(version = 3)).whenever(listingClient).get(501)

        val result = mvc.perform(location()).andReturn()

        assertEquals("listing/section", result.modelAndView?.viewName)
        assertEquals(3L, answers(result).version)
        assertEquals("Face au marché", answers(result).landmark)
        assertEquals(
            "Cette annonce a été modifiée entre-temps. Vos réponses sont conservées : vérifiez-les, puis enregistrez à nouveau.",
            model(result)["error"],
        )
    }

    @Test
    fun `save - sent meanwhile`() {
        doThrow(conflict(ErrorCode.LISTING_NOT_DRAFT)).whenever(listingClient).update(any(), any())

        val result = mvc.perform(location()).andReturn()

        assertEquals("redirect:/listings/501", result.modelAndView?.viewName)
        assertEquals(ListingController.CHANGED, result.flashMap[ListingController.OUTCOME])
    }

    @Test
    fun `save - already sent`() {
        doReturn(LISTING.copy(status = ListingStatus.UNDER_VERIFICATION)).whenever(listingClient).get(501)

        val result = mvc.perform(location()).andReturn()

        verify(listingClient, never()).update(any(), any())
        assertEquals(ListingController.CHANGED, result.flashMap[ListingController.OUTCOME])
    }

    @Test
    fun `save - another agent's listing`() {
        doReturn(LISTING.copy(agentId = 999)).whenever(listingClient).get(501)

        val result = mvc.perform(location()).andReturn()

        verify(listingClient, never()).update(any(), any())
        assertEquals("redirect:/listings/501", result.modelAndView?.viewName)
        assertEquals(ListingController.CHANGED, result.flashMap[ListingController.OUTCOME])
    }

    @Test
    fun `save - listing unavailable`() {
        doThrow(ResourceAccessException("down")).whenever(listingClient).get(501)

        val result = mvc.perform(location()).andReturn()

        assertEquals("redirect:/listings/501", result.modelAndView?.viewName)
        assertEquals(ListingController.FAILED, result.flashMap[ListingController.OUTCOME])
    }

    @Test
    fun `save - failure keeps the answers`() {
        doThrow(ResourceAccessException("down")).whenever(listingClient).update(any(), any())

        val result = mvc.perform(location()).andReturn()

        assertEquals("listing/section", result.modelAndView?.viewName)
        assertEquals("Face au marché", answers(result).landmark)
        assertEquals("Enregistrement impossible pour le moment. Réessayez dans quelques instants.", model(result)["error"])
    }

    @Test
    fun `save - unknown section or photos`() {
        assertEquals(404, mvc.perform(post("/listings/501/garden").param("version", "2")).andReturn().response.status)
        verify(listingClient, never()).update(any(), any())
    }

    @Test
    fun `save - malformed id`() {
        val result = mvc.perform(post("/listings/abc/location")).andReturn()

        assertEquals("redirect:/", result.modelAndView?.viewName)
        verify(listingClient, never()).get(any())
    }

    @Test
    fun `save - signed out`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/login", mvc.perform(location()).andReturn().modelAndView?.viewName)
    }
}
