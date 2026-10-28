package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.dto.UpdateListingRequest
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.dao.ListingSectionRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.text.SimpleDateFormat
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/listing/UpdateListingEndpoint.sql"])
class UpdateListingEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN) // The changes: see ListingAccessEndpointTest for the others

    @Autowired
    private lateinit var dao: ListingRepository

    @Autowired
    private lateinit var sectionDao: ListingSectionRepository

    @Test
    fun location() {
        val response = update(
            UpdateListingRequest(
                version = 3,
                section = ListingSection.LOCATION,
                neighborhoodId = 222L,
                landmark = "  Behind the bank ",
                price = 1, // Not of the section: ignored
            )
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val listing = listing()
        assertEquals(222L, listing.neighborhoodId)
        assertEquals("Behind the bank", listing.landmark)
        assertNull(listing.accessDirections) // Cleared
        assertEquals(2370201L, listing.cityId) // Never changes
        assertEquals(150000L, listing.price)
        assertEquals(11.5, listing.longitude)
        assertNotEquals(3L, listing.version)
        assertUnchanged(listing)

        // Verified again: the date of this save
        val section = sectionDao.findByListingIdAndSection(100L, ListingSection.LOCATION)
        assertTrue(section!!.verifiedAt.after(SimpleDateFormat("yyyy-MM-dd").parse("2026-10-03")))
        assertEquals(1, sectionDao.findByListingId(100L).size)
    }

    @Test
    fun gps() {
        val response = update(
            UpdateListingRequest(version = 3, section = ListingSection.GPS, latitude = 4.05, longitude = 9.7)
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val listing = listing()
        assertEquals(4.05, listing.latitude)
        assertEquals(9.7, listing.longitude)
        assertEquals("Near the embassy", listing.landmark)
        assertUnchanged(listing)
        assertNotNull(sectionDao.findByListingIdAndSection(100L, ListingSection.GPS))
    }

    @Test
    fun property() {
        val response = update(
            UpdateListingRequest(
                version = 3,
                section = ListingSection.PROPERTY,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = PropertyType.HOUSE,
                furnitureType = FurnitureType.UNFURNISHED,
                rooms = 4,
                bathrooms = 2,
                livingRooms = 1,
                kitchens = 1,
                area = 200,
                levels = 2,
            )
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val listing = listing()
        assertEquals(PropertyCategory.RESIDENTIAL, listing.propertyCategory)
        assertEquals(PropertyType.HOUSE, listing.propertyType)
        assertEquals(FurnitureType.UNFURNISHED, listing.furnitureType)
        assertEquals(4, listing.rooms)
        assertEquals(2, listing.bathrooms)
        assertEquals(1, listing.livingRooms)
        assertEquals(1, listing.kitchens)
        assertNull(listing.parkings) // Cleared
        assertEquals(200, listing.area)
        assertEquals(2, listing.levels)
        assertNull(listing.floorLevel) // Cleared: a house has no floor
        assertEquals("Near the embassy", listing.landmark)
        assertUnchanged(listing)
        assertNotNull(sectionDao.findByListingIdAndSection(100L, ListingSection.PROPERTY))
    }

    @Test
    fun amenities() {
        val response = update(
            UpdateListingRequest(
                version = 3,
                section = ListingSection.AMENITIES,
                waterTank = true,
                solarPanel = true,
                trackInaccessibleByCar = true,
                videoSurveillance = true,
                waterMeter = false,
            )
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val listing = listing()
        assertFalse(listing.waterMeter)
        assertFalse(listing.dedicatedWaterMeter) // Not sent: not there
        assertTrue(listing.waterTank)
        assertFalse(listing.waterWell)
        assertFalse(listing.electricityMeter)
        assertFalse(listing.generatorSet)
        assertTrue(listing.solarPanel)
        assertFalse(listing.roadPaved)
        assertFalse(listing.trackAccessibleByCar)
        assertTrue(listing.trackInaccessibleByCar)
        assertFalse(listing.securedFenced)
        assertFalse(listing.securityGuard)
        assertTrue(listing.videoSurveillance)
        assertUnchanged(listing)
        assertNotNull(sectionDao.findByListingIdAndSection(100L, ListingSection.AMENITIES))
    }

    @Test
    fun terms() {
        val response = update(
            UpdateListingRequest(
                version = 3,
                section = ListingSection.TERMS,
                price = 175000,
                securityDeposit = 0,
                leaseTerm = 24,
                availableFrom = LocalDate.of(2027, 1, 15),
            )
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val listing = listing()
        assertEquals(175000L, listing.price)
        assertEquals(0, listing.securityDeposit)
        assertNull(listing.advanceRent) // Cleared
        assertEquals(24, listing.leaseTerm)
        assertNull(listing.noticePeriod) // Cleared
        assertEquals(LocalDate.of(2027, 1, 15), listing.availableFrom)
        assertEquals("XAF", listing.currencyCode)
        assertUnchanged(listing)
        assertNotNull(sectionDao.findByListingIdAndSection(100L, ListingSection.TERMS))
    }

    @Test
    fun remarks() {
        val response = update(UpdateListingRequest(version = 3, section = ListingSection.REMARKS, agentRemarks = " "))

        assertEquals(HttpStatus.OK, response.statusCode)

        val listing = listing()
        assertNull(listing.agentRemarks)
        assertEquals(150000L, listing.price)
        assertNotNull(sectionDao.findByListingIdAndSection(100L, ListingSection.REMARKS))
    }

    @Test
    fun `required field missing - 400`() {
        val response = update(
            UpdateListingRequest(
                version = 3,
                section = ListingSection.PROPERTY,
                propertyCategory = PropertyCategory.RESIDENTIAL,
                propertyType = PropertyType.HOUSE,
            )
        )

        assertBadRequest(response.body, "area")
        assertNull(sectionDao.findByListingIdAndSection(100L, ListingSection.PROPERTY))
    }

    @Test
    fun `neighborhood of another city - 400`() {
        val response = update(
            UpdateListingRequest(
                version = 3,
                section = ListingSection.LOCATION,
                neighborhoodId = 333L,
                landmark = "Behind the bank",
            )
        )

        assertBadRequest(response.body, "neighborhoodId")
        assertEquals(111L, listing().neighborhoodId)
    }

    @Test
    fun `unknown neighborhood - 400`() {
        val response = update(
            UpdateListingRequest(
                version = 3,
                section = ListingSection.LOCATION,
                neighborhoodId = 999L,
                landmark = "Behind the bank",
            )
        )

        assertBadRequest(response.body, "neighborhoodId")
    }

    @Test
    fun `city as neighborhood - 400`() {
        val response = update(
            UpdateListingRequest(
                version = 3,
                section = ListingSection.LOCATION,
                neighborhoodId = 2370201L,
                landmark = "Behind the bank",
            )
        )

        assertBadRequest(response.body, "neighborhoodId")
    }

    @Test
    fun `invalid position - 400`() {
        val response = update(
            UpdateListingRequest(version = 3, section = ListingSection.GPS, latitude = 91.0, longitude = 9.7)
        )

        assertBadRequest(response.body, "latitude")
    }

    @Test
    fun `no road access - 400`() {
        val response = update(UpdateListingRequest(version = 3, section = ListingSection.AMENITIES, waterTank = true))

        assertBadRequest(response.body, "roadPaved")
    }

    @Test
    fun `two road accesses - 400`() {
        val response = update(
            UpdateListingRequest(
                version = 3,
                section = ListingSection.AMENITIES,
                roadPaved = true,
                trackAccessibleByCar = true,
            )
        )

        assertBadRequest(response.body, "roadPaved")
    }

    @Test
    fun `no section - 400`() {
        val response = update(UpdateListingRequest(version = 3, landmark = "Behind the bank"))

        assertBadRequest(response.body, "section")
    }

    @Test
    fun `photos - 400`() {
        val response = update(UpdateListingRequest(version = 3, section = ListingSection.PHOTOS))

        assertBadRequest(response.body, "section")
    }

    @Test
    fun `no version - 409`() {
        val response = update(
            UpdateListingRequest(section = ListingSection.REMARKS, agentRemarks = "Call first")
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_VERSION_CONFLICT, response.body?.error?.code)
        assertEquals("Keys at the guard", listing().agentRemarks)
        assertNull(sectionDao.findByListingIdAndSection(100L, ListingSection.REMARKS))
    }

    @Test
    fun `stale version - 409`() {
        val response = update(
            UpdateListingRequest(version = 2, section = ListingSection.REMARKS, agentRemarks = "Call first")
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_VERSION_CONFLICT, response.body?.error?.code)
        assertEquals("Keys at the guard", listing().agentRemarks)
    }

    @Test
    fun `not draft - 409`() {
        val response = update(
            UpdateListingRequest(version = 0, section = ListingSection.REMARKS, agentRemarks = "Call first"),
            id = 102L,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_DRAFT, response.body?.error?.code)
        assertNull(dao.findById(102L).get().agentRemarks)
        assertNull(sectionDao.findByListingIdAndSection(102L, ListingSection.REMARKS))
    }

    @Test
    fun `anonymous - 401`() {
        anonymousUser = true

        val response = update(
            UpdateListingRequest(version = 3, section = ListingSection.REMARKS, agentRemarks = "Call first")
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        assertEquals("Keys at the guard", listing().agentRemarks)
    }

    @Test
    fun `not found`() {
        val response = update(
            UpdateListingRequest(version = 0, section = ListingSection.REMARKS),
            id = 999L,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `other tenant`() {
        val response = update(
            UpdateListingRequest(version = 0, section = ListingSection.REMARKS),
            id = 900L,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    private fun update(request: UpdateListingRequest, id: Long = 100L) =
        rest.postForEntity("/v1/listings/$id", request, ErrorResponse::class.java)

    private fun listing(): ListingEntity = dao.findById(100L).get()

    // What no section changes
    private fun assertUnchanged(listing: ListingEntity) {
        assertEquals(TENANT_ID, listing.tenantId)
        assertEquals(1L, listing.mandate.id)
        assertEquals(20L, listing.agent.id)
        assertEquals(ListingStatus.DRAFT, listing.status)
        assertEquals("Nice apartment", listing.title)
        assertEquals("Keys at the guard", listing.agentRemarks)
    }

    private fun assertBadRequest(body: ErrorResponse?, parameter: String) {
        assertNotNull(body)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, body?.error?.code)
        assertEquals(parameter, body?.error?.parameter?.name)
        assertEquals(150000L, listing().price)
        assertEquals(3L, listing().version)
    }
}
