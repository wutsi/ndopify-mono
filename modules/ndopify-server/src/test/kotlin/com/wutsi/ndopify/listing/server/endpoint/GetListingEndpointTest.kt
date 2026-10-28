package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.GetListingResponse
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/listing/GetListingEndpoint.sql"])
class GetListingEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN) // For GET /v1/mandates/{id}

    @Test
    fun get() {
        val response = rest.getForEntity("/v1/listings/100", GetListingResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val listing = response.body!!.listing
        assertEquals(100L, listing.id)
        assertEquals(3L, listing.version)
        assertEquals(1L, listing.mandateId)
        assertEquals(20L, listing.agentId)
        assertEquals(PropertyCategory.RESIDENTIAL, listing.propertyCategory)
        assertEquals(ListingType.RENTAL, listing.listingType)
        assertEquals(ListingStatus.PUBLISHED, listing.status)
        assertEquals(2370201L, listing.cityId)
        assertEquals(111L, listing.neighborhoodId)
        assertEquals("Near the embassy", listing.landmark)
        assertEquals("Second street on the left", listing.accessDirections)
        assertEquals(11.5, listing.longitude)
        assertEquals(3.9, listing.latitude)
        assertEquals(PropertyType.APARTMENT, listing.propertyType)
        assertEquals(FurnitureType.FULLY_FURNISHED, listing.furnitureType)
        assertEquals(2, listing.rooms)
        assertEquals(1, listing.bathrooms)
        assertEquals(1, listing.livingRooms)
        assertEquals(1, listing.kitchens)
        assertEquals(1, listing.parkings)
        assertEquals(75, listing.area)
        assertEquals(1, listing.levels)
        assertEquals(3, listing.floorLevel)
        assertTrue(listing.waterMeter)
        assertTrue(listing.dedicatedWaterMeter)
        assertFalse(listing.waterTank)
        assertFalse(listing.waterWell)
        assertTrue(listing.electricityMeter)
        assertTrue(listing.dedicatedElectricityMeter)
        assertTrue(listing.generatorSet)
        assertFalse(listing.solarPanel)
        assertTrue(listing.roadPaved)
        assertTrue(listing.trackAccessibleByCar)
        assertFalse(listing.trackInaccessibleByCar)
        assertTrue(listing.securedFenced)
        assertTrue(listing.securityGuard)
        assertFalse(listing.videoSurveillance)
        assertEquals(150000L, listing.price)
        assertEquals("XAF", listing.currencyCode)
        assertEquals(PeriodicityType.MONTHLY, listing.pricePeriodicityType)
        assertEquals(2, listing.securityDeposit)
        assertEquals(3, listing.advanceRent)
        assertEquals(12, listing.leaseTerm)
        assertEquals(1, listing.noticePeriod)
        assertEquals(LocalDate.of(2026, 11, 1), listing.availableFrom)
        assertEquals("Keys at the guard", listing.agentRemarks)
        assertEquals("Nice apartment", listing.title)
        assertEquals("Two rooms in Bastos", listing.summary)
        assertEquals("A long description", listing.description)
        assertNotNull(listing.publishedAt)

        assertEquals(ListingSection.entries.drop(1), listing.sections.map { it.section })
        val sections = listing.sections.associateBy { it.section }
        assertEquals(ListingSectionStatus.VERIFIED, sections[ListingSection.LOCATION]?.status)
        assertNotNull(sections[ListingSection.LOCATION]?.verifiedAt)
        assertEquals(ListingSectionStatus.TO_VERIFY, sections[ListingSection.GPS]?.status)
        assertNull(sections[ListingSection.GPS]?.verifiedAt)
        assertEquals(ListingSectionStatus.TO_VERIFY, sections[ListingSection.PROPERTY]?.status)
        assertEquals(ListingSectionStatus.TO_VERIFY, sections[ListingSection.AMENITIES]?.status)
        assertEquals(ListingSectionStatus.VERIFIED, sections[ListingSection.TERMS]?.status)
        assertEquals(ListingSectionStatus.VERIFIED, sections[ListingSection.PHOTOS]?.status)
        assertNull(sections[ListingSection.PHOTOS]?.verifiedAt)
        assertEquals(ListingSectionStatus.TO_VERIFY, sections[ListingSection.REMARKS]?.status)
        assertTrue(listing.sections.filter { it.section != ListingSection.REMARKS }.all { it.required })
        assertFalse(sections[ListingSection.REMARKS]!!.required)
    }

    @Test
    fun `photos - one rejected`() {
        val response = rest.getForEntity("/v1/listings/101", GetListingResponse::class.java)

        val sections = response.body!!.listing.sections.associateBy { it.section }
        assertEquals(ListingSectionStatus.TO_VERIFY, sections[ListingSection.PHOTOS]?.status)
        assertTrue(sections.values.all { it.status == ListingSectionStatus.TO_VERIFY })
    }

    @Test
    fun `public - no token needed`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/listings/100", GetListingResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(100L, response.body!!.listing.id)
    }

    @Test
    fun `public - not published, not found without a token`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/listings/101", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `not published - admin`() {
        val response = rest.getForEntity("/v1/listings/101", GetListingResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(ListingStatus.DRAFT, response.body!!.listing.status)
    }

    @Test
    fun `mandate - has its listing id`() {
        val response = rest.getForEntity("/v1/mandates/1", GetMandateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(100L, response.body!!.mandate.listingId)
    }

    @Test
    fun `not found`() {
        val response = rest.getForEntity("/v1/listings/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `other tenant`() {
        val response = rest.getForEntity("/v1/listings/900", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }
}
