package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.dto.UpdateListingRequest
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/listing/UpdateListingEndpoint.sql"])
class UpdateListingEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: ListingRepository

    private val request = UpdateListingRequest(
        propertyCategory = PropertyCategory.COMMERCIAL,
        propertyType = PropertyType.OFFICE,
        cityId = 2220957L,
        neighborhoodId = 222L,
        landmark = "Behind the bank",
        accessDirections = "Third floor",
        longitude = 9.7,
        latitude = 4.05,
        furnitureType = FurnitureType.UNFURNISHED,
        rooms = 5,
        bathrooms = 2,
        livingRooms = 0,
        kitchens = 1,
        parkings = 3,
        area = 300,
        levels = 1,
        floorLevel = 3,
        waterMeter = false,
        dedicatedWaterMeter = false,
        waterTank = true,
        waterWell = true,
        electricityMeter = false,
        dedicatedElectricityMeter = false,
        generatorSet = false,
        solarPanel = true,
        roadPaved = false,
        trackAccessibleByCar = false,
        trackInaccessibleByCar = true,
        securedFenced = false,
        securityGuard = false,
        videoSurveillance = true,
        price = 500000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
        securityDeposit = 4,
        advanceRent = 6,
        leaseTerm = 24,
        noticePeriod = 3,
        availableFrom = LocalDate.of(2027, 1, 15),
        agentRemarks = "Call before visiting",
    )

    @Test
    fun update() {
        val response = rest.postForEntity("/v1/listings/100", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val listing = dao.findById(100L).get()
        assertEquals(TENANT_ID, listing.tenantId)
        assertEquals(1L, listing.mandate.id) // Unchanged
        assertEquals(20L, listing.agent.id)
        assertEquals(ListingStatus.PUBLISHED, listing.status)
        assertEquals("Nice apartment", listing.title)
        assertEquals(PropertyCategory.COMMERCIAL, listing.propertyCategory)
        assertEquals(PropertyType.OFFICE, listing.propertyType)
        assertEquals(2220957L, listing.cityId)
        assertEquals(222L, listing.neighborhoodId)
        assertEquals("Behind the bank", listing.landmark)
        assertEquals("Third floor", listing.accessDirections)
        assertEquals(9.7, listing.longitude)
        assertEquals(4.05, listing.latitude)
        assertEquals(FurnitureType.UNFURNISHED, listing.furnitureType)
        assertEquals(5, listing.rooms)
        assertEquals(2, listing.bathrooms)
        assertEquals(0, listing.livingRooms)
        assertEquals(1, listing.kitchens)
        assertEquals(3, listing.parkings)
        assertEquals(300, listing.area)
        assertEquals(1, listing.levels)
        assertEquals(3, listing.floorLevel)
        assertFalse(listing.waterMeter)
        assertTrue(listing.waterTank)
        assertTrue(listing.waterWell)
        assertTrue(listing.solarPanel)
        assertTrue(listing.trackInaccessibleByCar)
        assertTrue(listing.videoSurveillance)
        assertEquals(500000L, listing.price)
        assertEquals(4, listing.securityDeposit)
        assertEquals(6, listing.advanceRent)
        assertEquals(24, listing.leaseTerm)
        assertEquals(3, listing.noticePeriod)
        assertEquals(LocalDate.of(2027, 1, 15), listing.availableFrom)
        assertEquals("Call before visiting", listing.agentRemarks)
        assertNotEquals(3L, listing.version)
    }

    @Test
    fun `same version`() {
        val response = rest.postForEntity("/v1/listings/100", request.copy(version = 3), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(500000L, dao.findById(100L).get().price)
    }

    @Test
    fun `stale version`() {
        val response = rest.postForEntity("/v1/listings/100", request.copy(version = 2), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_VERSION_CONFLICT, response.body?.error?.code)
        assertEquals(150000L, dao.findById(100L).get().price)
    }

    @Test
    fun `anonymous - 401`() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/listings/100", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        assertEquals(150000L, dao.findById(100L).get().price)
    }

    @Test
    fun `not found`() {
        val response = rest.postForEntity("/v1/listings/999", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `other tenant`() {
        val response = rest.postForEntity("/v1/listings/900", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }
}
