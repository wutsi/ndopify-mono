package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.CreateListingRequest
import com.wutsi.ndopify.listing.dto.CreateListingResponse
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/listing/CreateListingEndpoint.sql"])
class CreateListingEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN) // The changes: see ListingAccessEndpointTest for the others

    @Autowired
    private lateinit var dao: ListingRepository

    @Test
    fun create() {
        val response = rest.postForEntity(
            "/v1/listings",
            CreateListingRequest(mandateId = 1),
            CreateListingResponse::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val listing = dao.findById(response.body!!.listingId!!).get()
        assertEquals(TENANT_ID, listing.tenantId)
        assertEquals(1L, listing.mandate.id)
        assertEquals(20L, listing.agent.id)
        assertEquals(ListingStatus.DRAFT, listing.status)
        assertEquals(PropertyCategory.RESIDENTIAL, listing.propertyCategory)
        assertEquals(ListingType.RENTAL, listing.listingType)
        assertEquals(2370201L, listing.cityId)
        assertTrue(listing.neighborhoodId in listOf(111L, 222L))
        assertEquals(PropertyType.APARTMENT, listing.propertyType)
        assertEquals(FurnitureType.FULLY_FURNISHED, listing.furnitureType)
        assertEquals(2, listing.rooms) // "2+"
        assertEquals(1, listing.parkings)
        assertEquals(75, listing.area)
        assertTrue(listing.waterMeter)
        assertTrue(listing.dedicatedWaterMeter)
        assertFalse(listing.waterTank)
        assertFalse(listing.waterWell)
        assertTrue(listing.electricityMeter)
        assertFalse(listing.dedicatedElectricityMeter)
        assertTrue(listing.generatorSet)
        assertFalse(listing.solarPanel)
        assertTrue(listing.roadPaved)
        assertFalse(listing.trackAccessibleByCar)
        assertFalse(listing.trackInaccessibleByCar)
        assertTrue(listing.securedFenced)
        assertFalse(listing.securityGuard)
        assertTrue(listing.videoSurveillance)
        assertEquals(150000L, listing.price)
        assertEquals("XAF", listing.currencyCode)
        assertEquals(PeriodicityType.MONTHLY, listing.pricePeriodicityType)
        assertEquals(2, listing.securityDeposit)
        assertEquals(3, listing.advanceRent)
        assertEquals(12, listing.leaseTerm)
        assertEquals(1, listing.noticePeriod)
        assertEquals(LocalDate.of(2026, 12, 1), listing.availableFrom)
        assertNull(listing.title)
        assertNull(listing.publishedAt)
        assertNull(listing.closedAt)
    }

    @Test
    fun `neighborhood of another city - none`() {
        val response = rest.postForEntity(
            "/v1/listings",
            CreateListingRequest(mandateId = 4),
            CreateListingResponse::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNull(dao.findById(response.body!!.listingId!!).get().neighborhoodId)
    }

    @Test
    fun `anonymous - 401`() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/listings", CreateListingRequest(mandateId = 1), ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        assertEquals(1, dao.count().toInt()) // Only listing 300
    }

    @Test
    fun `mandate without agent`() {
        val response = rest.postForEntity("/v1/listings", CreateListingRequest(mandateId = 2), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_ASSIGNED, response.body?.error?.code)
    }

    @Test
    fun `mandate with a listing already`() {
        val response = rest.postForEntity("/v1/listings", CreateListingRequest(mandateId = 3), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_ALREADY_EXISTS, response.body?.error?.code)
    }

    @Test
    fun `mandate not found`() {
        val response = rest.postForEntity("/v1/listings", CreateListingRequest(mandateId = 999), ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `mandate of another tenant`() {
        val response = rest.postForEntity("/v1/listings", CreateListingRequest(mandateId = 9), ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }
}
