package com.wutsi.ndopify.web.ui.listing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.web.client.ndopify.NdListingClient
import com.wutsi.ndopify.web.client.ndopify.NdLocationClient
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.service.TenantFixtures.TENANT
import com.wutsi.ndopify.web.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.web.ui.layout.TenantAdvice
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

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
    private val context = mock<TenantContext> { on { getTenant() } doReturn TENANT }
    private val clock = Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC)

    private fun request(path: String): MvcResult =
        MockMvcBuilders.standaloneSetup(ListingController(listingClient, locationClient, clock))
            .setControllerAdvice(TenantAdvice(context), HttpClientErrorHandler())
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
            .perform(get(path))
            .andReturn()

    @Test
    fun `listing page`() {
        val result = request("/douala/bonapriso/listing/123/appartement")

        assertEquals(ListingController.VIEW, result.modelAndView?.viewName)
        val page = result.modelAndView?.model?.get("page") as ListingPage
        assertEquals(123L, page.id)
        assertEquals("Appartement meublé", page.title)
        assertEquals("Bonapriso, Douala", page.place)
        assertEquals("2026-10-31", page.visitDays.first().value)
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
}
