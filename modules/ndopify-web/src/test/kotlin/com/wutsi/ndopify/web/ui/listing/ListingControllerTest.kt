package com.wutsi.ndopify.web.ui.listing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.web.client.ndopify.NdListingClient
import com.wutsi.ndopify.web.client.ndopify.NdLocationClient
import com.wutsi.ndopify.web.client.ndopify.NdMandateClient
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import com.wutsi.ndopify.web.service.TenantFixtures.TENANT
import com.wutsi.ndopify.web.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.web.ui.layout.TenantAdvice
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingControllerTest {
    private val listing = Listing(
        id = 123,
        status = ListingStatus.PUBLISHED,
        cityId = 2370201,
        neighborhoodId = 5,
        title = "Appartement meublé",
        url = "/douala/bonapriso/listing/123/appartement",
    )
    private val listingClient = mock<NdListingClient> {
        on { get(123) } doReturn listing
        on { images(123) } doReturn listOf(ListingImage(id = 1, url = "https://cdn/1.jpg"))
    }
    private val locationClient = mock<NdLocationClient> {
        on { search(any()) } doReturn listOf(Location(id = 2370201, name = "Douala"), Location(id = 5, name = "Bonapriso"))
    }
    private val mandateClient = mock<NdMandateClient>()
    private val accessTokenService = mock<AccessTokenService>()
    private val context = mock<TenantContext> { on { getTenant() } doReturn TENANT }
    private val clock = Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC)

    private fun perform(request: MockHttpServletRequestBuilder): MvcResult =
        MockMvcBuilders.standaloneSetup(ListingController(listingClient, locationClient, mandateClient, clock))
            .setControllerAdvice(TenantAdvice(context), HttpClientErrorHandler(accessTokenService))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
            .perform(request)
            .andReturn()

    private fun request(path: String): MvcResult = perform(get(path))

    @Test
    fun `token rejected by the server clears the cookie and reloads the page`() {
        doReturn("aaa.bbb.ccc").`when`(accessTokenService).get()
        doThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "", HttpHeaders.EMPTY, null, null)).`when`(listingClient).get(123)

        val result = request("/douala/bonapriso/listing/123/appartement")

        assertEquals(302, result.response.status)
        assertEquals("/douala/bonapriso/listing/123/appartement", result.response.redirectedUrl)
        verify(accessTokenService).remove()
    }

    @Test
    fun `listing page`() {
        val result = request("/douala/bonapriso/listing/123/appartement")

        assertEquals(ListingController.VIEW, result.modelAndView?.viewName)
        val page = result.modelAndView?.model?.get("page") as ListingPage
        assertEquals(123L, page.id)
        assertEquals("Appartement meublé", page.title)
        assertEquals("Bonapriso, Douala", page.place)
        assertEquals(false, result.modelAndView?.model?.get("done"))
        verify(locationClient).search(listOf(2370201L, 5L))
    }

    @Test
    fun `redirects a stale slug to the listing's URL`() {
        val result = request("/douala/bonapriso/listing/123/old")

        assertEquals(HttpStatus.MOVED_PERMANENTLY.value(), result.response.status)
        assertEquals("/douala/bonapriso/listing/123/appartement", result.response.getHeader("Location"))
        verify(listingClient, never()).images(any())
    }

    @Test
    fun `redirects a URL without slug`() {
        val result = request("/douala/listing/123")

        assertEquals(HttpStatus.MOVED_PERMANENTLY.value(), result.response.status)
        assertEquals("/douala/bonapriso/listing/123/appartement", result.response.getHeader("Location"))
    }

    @Test
    fun `404 when the server doesn't publish it`() {
        doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", HttpHeaders.EMPTY, null, null)).`when`(listingClient).get(123)

        assertEquals(404, request("/douala/bonapriso/listing/123/appartement").response.status)
    }

    @Test
    fun `404 when its images are gone`() {
        doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", HttpHeaders.EMPTY, null, null)).`when`(listingClient).images(123)

        assertEquals(404, request("/douala/bonapriso/listing/123/appartement").response.status)
    }

    private fun interest(vararg params: Pair<String, String>) =
        perform(post("/listings/123/interest").apply { params.forEach { (name, value) -> param(name, value) } })

    @Test
    fun `interest creates a mandate and redirects to the confirmation`() {
        val result = interest(
            "firstName" to "Awa",
            "lastName" to "Ndiaye",
            "email" to "awa.ndiaye@gmail.com",
            "whatsappNumber" to "+237650000001",
            "movingPeriod" to "WITHIN_1_MONTH",
        )

        assertEquals(302, result.response.status)
        assertEquals(
            "/douala/bonapriso/listing/123/appartement?interest=done#visit",
            result.response.redirectedUrl,
        )
        val request = argumentCaptor<CreateMandateRequest>()
        verify(mandateClient).create(request.capture())
        assertEquals(123L, request.firstValue.originListingId)
        assertEquals(MandateType.SEARCH, request.firstValue.mandateType)
        assertEquals("Awa", request.firstValue.firstName)
        assertEquals("Ndiaye", request.firstValue.lastName)
        assertEquals(MovingPeriod.WITHIN_1_MONTH, request.firstValue.movingPeriod)
    }

    @Test
    fun `the honeypot shows the confirmation without creating anything`() {
        val result = interest(
            "firstName" to "Awa",
            "lastName" to "Ndiaye",
            "email" to "awa.ndiaye@gmail.com",
            "whatsappNumber" to "+237650000001",
            "movingPeriod" to "WITHIN_1_MONTH",
            "website" to "https://spam.example",
        )

        assertEquals(302, result.response.status)
        assertEquals(
            "/douala/bonapriso/listing/123/appartement?interest=done#visit",
            result.response.redirectedUrl,
        )
        verify(mandateClient, never()).create(any())
    }

    @Test
    fun `invalid contact details re-render the form with French errors`() {
        val result = interest()

        assertEquals(ListingController.VIEW, result.modelAndView?.viewName)
        val errors = result.modelAndView?.model?.get("errors") as Map<*, *>
        assertEquals("Indiquez votre prénom.", errors["firstName"])
        assertEquals("Saisissez une adresse email valide.", errors["email"])
        assertEquals("Choisissez votre date d'emménagement.", errors["movingPeriod"])
        assertEquals(false, result.modelAndView?.model?.get("done"))
        assertNotNull(result.modelAndView?.model?.get("interestForm"))
        verify(mandateClient, never()).create(any())
    }

    @Test
    fun `a sale listing asks when the buyer wants to buy`() {
        doReturn(listing.copy(listingType = ListingType.SALE)).whenever(listingClient).get(123)

        val result = interest("firstName" to "Awa", "lastName" to "Ndiaye", "email" to "awa.ndiaye@gmail.com", "whatsappNumber" to "+237650000001")

        val page = result.modelAndView?.model?.get("page") as ListingPage
        assertTrue(page.sale)
        val errors = result.modelAndView?.model?.get("errors") as Map<*, *>
        assertEquals("Choisissez quand vous souhaitez acheter.", errors["movingPeriod"])
        verify(mandateClient, never()).create(any())
    }

    @Test
    fun `a rental listing keeps the moving date message`() {
        val result = interest("firstName" to "Awa", "lastName" to "Ndiaye", "email" to "awa.ndiaye@gmail.com", "whatsappNumber" to "+237650000001")

        val errors = result.modelAndView?.model?.get("errors") as Map<*, *>
        assertEquals("Choisissez votre date d'emménagement.", errors["movingPeriod"])
    }

    @Test
    fun `the listing is read once more to word a missing horizon`() {
        interest("firstName" to "Awa", "lastName" to "Ndiaye", "email" to "awa.ndiaye@gmail.com", "whatsappNumber" to "+237650000001")

        // The type lookup, then the page's own read
        verify(listingClient, times(2)).get(123)
    }

    @Test
    fun `no extra read of the listing when another field is invalid`() {
        interest("lastName" to "Ndiaye", "email" to "awa.ndiaye@gmail.com", "whatsappNumber" to "+237650000001", "movingPeriod" to "WITHIN_1_MONTH")

        verify(listingClient, times(1)).get(123)
    }

    @Test
    fun `no extra read of the listing on a valid submission`() {
        interest(
            "firstName" to "Awa",
            "lastName" to "Ndiaye",
            "email" to "awa.ndiaye@gmail.com",
            "whatsappNumber" to "+237650000001",
            "movingPeriod" to "WITHIN_1_MONTH",
        )

        // Only the confirmation's own read to know where to redirect
        verify(listingClient, times(1)).get(123)
    }

    @Test
    fun `a rate-limited request shows the same confirmation`() {
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "", HttpHeaders.EMPTY, null, null))
            .whenever(mandateClient).create(any())

        val result = interest(
            "firstName" to "Awa",
            "lastName" to "Ndiaye",
            "email" to "awa.ndiaye@gmail.com",
            "whatsappNumber" to "+237650000001",
            "movingPeriod" to "WITHIN_1_MONTH",
        )

        assertEquals(302, result.response.status)
        assertEquals("/douala/bonapriso/listing/123/appartement?interest=done#visit", result.response.redirectedUrl)
    }

    @Test
    fun `a rejected request re-renders the form with a global error`() {
        doThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "", HttpHeaders.EMPTY, null, null))
            .whenever(mandateClient).create(any())

        val result = interest(
            "firstName" to "Awa",
            "lastName" to "Ndiaye",
            "email" to "awa.ndiaye@gmail.com",
            "whatsappNumber" to "+237650000001",
            "movingPeriod" to "WITHIN_1_MONTH",
        )

        assertEquals(ListingController.VIEW, result.modelAndView?.viewName)
        val errors = result.modelAndView?.model?.get("errors") as Map<*, *>
        assertNotNull(errors["global"])
    }

    @Test
    fun `a refused e-mail re-renders the popup with its field error`() {
        doThrow(badRequest(ErrorCode.EMAIL_ADDRESS_INVALID)).whenever(mandateClient).create(any())

        val result = interest(
            "firstName" to "Awa",
            "lastName" to "Ndiaye",
            "email" to "awa.ndiaye@gmail.com",
            "whatsappNumber" to "+237650000001",
            "movingPeriod" to "WITHIN_1_MONTH",
        )

        val errors = result.modelAndView?.model?.get("errors") as Map<*, *>
        assertEquals("Cette adresse email n'est pas valide.", errors["email"])
        assertNull(errors["global"])
        assertNotNull(result.modelAndView?.model?.get("interestForm")) // The typed values are kept
        assertEquals(false, result.modelAndView?.model?.get("done"))
    }

    @Test
    fun `another 400 keeps the global message`() {
        doThrow(badRequest(ErrorCode.HTTP_INVALID_PARAMETER)).whenever(mandateClient).create(any())

        val result = interest(
            "firstName" to "Awa",
            "lastName" to "Ndiaye",
            "email" to "awa.ndiaye@gmail.com",
            "whatsappNumber" to "+237650000001",
            "movingPeriod" to "WITHIN_1_MONTH",
        )

        val errors = result.modelAndView?.model?.get("errors") as Map<*, *>
        assertNotNull(errors["global"])
        assertNull(errors["email"])
    }

    @Test
    fun `the confirmation state of the page comes from the URL`() {
        val result = perform(get("/douala/bonapriso/listing/123/appartement").param("interest", "done"))

        assertEquals(ListingController.VIEW, result.modelAndView?.viewName)
        assertEquals(true, result.modelAndView?.model?.get("done"))
    }

    private fun badRequest(code: String): HttpClientErrorException.BadRequest {
        val ex = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, null, null)
            as HttpClientErrorException.BadRequest
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        return ex
    }
}
