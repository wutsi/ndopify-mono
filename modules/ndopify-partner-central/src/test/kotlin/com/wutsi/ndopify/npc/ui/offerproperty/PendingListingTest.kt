package com.wutsi.ndopify.npc.ui.offerproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingListingTest {
    companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 21)

        val COMPLETE = PendingListing(
            listingType = ListingType.RENTAL,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            cityId = 2220957,
            neighborhoodId = 1001,
            priceAmount = 150_000,
            advanceRent = 3,
            securityDeposit = 2,
            availableFrom = TODAY.plusDays(10),
            propertyType = "APARTMENT",
            furnitureType = "UNFURNISHED",
            rooms = "3",
            parkings = "0",
            roadAccess = "roadPaved",
            amenities = setOf("waterMeter"),
            firstName = "Ray",
            lastName = "Sponsible",
            email = "ray@gmail.com",
            whatsappNumber = "+237670000001",
            preferredContactPeriod = ContactPeriodType.MORNING,
        )
    }

    private fun step(pending: PendingListing) = pending.firstIncompleteStep(TODAY)

    private val buildingSale = COMPLETE.copy(
        listingType = ListingType.SALE,
        propertyCategory = PropertyCategory.COMMERCIAL,
        propertyType = "BUILDING",
        area = 850,
        priceAmount = 250_000_000,
        advanceRent = null,
        securityDeposit = null,
        furnitureType = null,
        rooms = null,
        parkings = null,
        levels = 3,
        units = 12,
        roadAccess = null,
    )

    @Test
    fun `a building sale skips the amenities step`() {
        assertEquals(
            listOf("property", "address", "terms", "features", "contact", "review"),
            buildingSale.steps(),
        )
        assertEquals("review", step(buildingSale))
        assertTrue(buildingSale.isComplete("amenities", TODAY))
        assertEquals(
            listOf("property", "address", "terms", "features", "amenities", "contact", "review"),
            COMPLETE.steps(),
        )
    }

    @Test
    fun `a building sale requires both counts`() {
        assertTrue(buildingSale.isComplete("features", TODAY))
        assertFalse(buildingSale.copy(levels = null).isComplete("features", TODAY))
        assertFalse(buildingSale.copy(units = null).isComplete("features", TODAY))
        assertEquals("features", step(buildingSale.copy(units = null)))
    }

    @Test
    fun `a building sale sends its counts and no amenity`() {
        val request = buildingSale.toCreateMandateRequest("XAF")

        assertEquals(3, request.levels)
        assertEquals(12, request.units)
        assertNull(request.rooms)
        assertNull(request.furnitureType)
        assertNull(request.parkings)
        assertFalse(request.waterMeter)
        assertFalse(request.roadPaved)
    }

    @Test
    fun `only a building sale sends the counts`() {
        assertNull(COMPLETE.toCreateMandateRequest("XAF").levels)
        assertNull(COMPLETE.toCreateMandateRequest("XAF").units)

        // A rental building keeps today's model: neither count.
        val rentalBuilding = COMPLETE.copy(
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = "BUILDING",
            rooms = null,
            furnitureType = null,
            levels = 3,
            units = 12,
        )
        assertNull(rentalBuilding.toCreateMandateRequest("XAF").levels)
        assertNull(rentalBuilding.toCreateMandateRequest("XAF").units)
    }

    @Test
    fun emptyStartsAtProperty() {
        assertEquals("property", step(PendingListing()))
    }

    @Test
    fun completeGoesToReview() {
        assertEquals("review", step(COMPLETE))
    }

    @Test
    fun unknownStepIsNeverComplete() {
        assertFalse(COMPLETE.isComplete("review", TODAY))
        assertFalse(COMPLETE.isComplete("nope", TODAY))
        assertTrue(COMPLETE.isComplete("property", TODAY))
    }

    @Test
    fun blankStepIsTheFirstIncomplete() {
        assertEquals("property", step(COMPLETE.copy(propertyCategory = null)))
        assertEquals("address", step(COMPLETE.copy(cityId = null)))
        assertEquals("address", step(COMPLETE.copy(neighborhoodId = null)))
        assertEquals("terms", step(COMPLETE.copy(priceAmount = null)))
        assertEquals("review", step(COMPLETE.copy(securityDeposit = null)))
        assertEquals("terms", step(COMPLETE.copy(availableFrom = null)))
        assertEquals("features", step(COMPLETE.copy(propertyType = null)))
        assertEquals("features", step(COMPLETE.copy(furnitureType = null)))
        assertEquals("features", step(COMPLETE.copy(rooms = null)))
        assertEquals("features", step(COMPLETE.copy(parkings = null)))
        assertEquals("amenities", step(COMPLETE.copy(roadAccess = null)))
        assertEquals("contact", step(COMPLETE.copy(firstName = null)))
        assertEquals("contact", step(COMPLETE.copy(lastName = null)))
        assertEquals("contact", step(COMPLETE.copy(email = "nope")))
        assertEquals("contact", step(COMPLETE.copy(whatsappNumber = null)))
        assertEquals("contact", step(COMPLETE.copy(preferredContactPeriod = null)))
    }

    @Test
    fun aDateValidYesterdayExpires() {
        assertEquals("terms", step(COMPLETE.copy(availableFrom = TODAY.minusDays(1))))
    }

    @Test
    fun stepsThatDontApplyAreNotAsked() {
        val studio = COMPLETE.copy(propertyType = "STUDIO", rooms = null)
        assertEquals("review", step(studio))
        val room = COMPLETE.copy(propertyType = "ROOM", rooms = null, parkings = null)
        assertEquals("review", step(room))
        val office = COMPLETE.copy(
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = "OFFICE",
            furnitureType = null,
            rooms = null,
            parkings = null,
            area = 40,
        )
        assertEquals("review", step(office))
        assertEquals("features", step(office.copy(area = null)))
        assertEquals("features", step(COMPLETE.copy(propertyType = "OFFICE")))
    }

    @Test
    fun toCreateMandateRequest() {
        val request = COMPLETE.toCreateMandateRequest("XAF")

        assertEquals(MandateType.LISTING, request.mandateType)
        assertEquals(ListingType.RENTAL, request.listingType)
        assertEquals(PropertyCategory.RESIDENTIAL, request.propertyCategory)
        assertEquals(2220957, request.cityId)
        assertEquals(listOf(1001L), request.neighbourhoodIds)
        assertEquals(PropertyType.APARTMENT, request.propertyType)
        assertEquals(FurnitureType.UNFURNISHED, request.furnitureType)
        assertEquals("3", request.rooms)
        assertEquals("0", request.parkings)
        assertNull(request.area)
        assertEquals(Money(150_000, "XAF", PeriodicityType.MONTHLY), request.price)
        assertEquals(3, request.advanceRent)
        assertEquals(2, request.securityDeposit)
        assertEquals(TODAY.plusDays(10), request.availableFrom)
        assertTrue(request.roadPaved)
        assertFalse(request.trackAccessibleByCar)
        assertFalse(request.trackInaccessibleByCar)
        assertTrue(request.waterMeter)
        assertFalse(request.securityGuard)
        assertEquals("Ray", request.firstName)
        assertEquals("ray@gmail.com", request.email)
        assertEquals("+237670000001", request.whatsappNumber)
        assertEquals(ContactPeriodType.MORNING, request.preferredContactPeriod)
    }

    @Test
    fun `a rental is the same request as before`() {
        val pending = PendingListing(
            listingType = ListingType.RENTAL,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            cityId = 1L,
            neighborhoodId = 2L,
            propertyType = "APARTMENT",
            rooms = "3",
            furnitureType = "UNFURNISHED",
            parkings = "1",
            area = 80,
            priceAmount = 150_000L,
            advanceRent = 2,
            securityDeposit = 2,
            availableFrom = TODAY.plusDays(10),
            firstName = "Sandrine",
            lastName = "Eboa",
            email = "s@example.com",
            whatsappNumber = "+237600000000",
            preferredContactPeriod = ContactPeriodType.MORNING,
        )

        val request = pending.toCreateMandateRequest("XAF")

        assertEquals(ListingType.RENTAL, request.listingType)
        assertEquals(PeriodicityType.MONTHLY, request.price.periodicity)
        assertEquals(2, request.advanceRent)
        assertEquals(2, request.securityDeposit)
        assertEquals(emptyList(), request.ownershipDocuments)
    }

    @Test
    fun `a land sale sends its documents at a one-off price and no rental terms`() {
        val pending = COMPLETE.copy(
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = "LAND",
            area = 800,
            priceAmount = 45_000_000L,
            ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
            advanceRent = 2,
            securityDeposit = 2,
            rooms = "3",
            furnitureType = "UNFURNISHED",
        )

        val request = pending.toCreateMandateRequest("XAF")

        assertEquals(ListingType.SALE, request.listingType)
        assertEquals(PeriodicityType.NONE, request.price.periodicity)
        assertEquals(
            listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
            request.ownershipDocuments,
        )
        assertNull(request.advanceRent)
        assertNull(request.securityDeposit)
        assertNull(request.rooms)
        assertNull(request.furnitureType)
        assertEquals(800, request.area)
    }

    @Test
    fun `a villa sale sends no document`() {
        val pending = COMPLETE.copy(
            listingType = ListingType.SALE,
            propertyType = "VILLA",
            ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE),
        )

        assertEquals(emptyList(), pending.toCreateMandateRequest("XAF").ownershipDocuments)
    }

    @Test
    fun `a land sale without a document is complete`() {
        val sale = COMPLETE.copy(
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
            ownershipDocuments = emptyList(),
        )

        assertTrue(sale.isComplete("features", TODAY))
        assertEquals("features", step(sale.copy(area = null)))
        assertTrue(sale.isComplete("terms", TODAY))
        assertEquals("terms", step(sale.copy(priceAmount = 900_000L)))
    }

    @Test
    fun toCreateMandateRequestDropsStaleAnswers() {
        val office = COMPLETE.copy(
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = "OFFICE",
            area = 40,
            roadAccess = "trackAccessibleByCar",
        ).toCreateMandateRequest("XAF")

        assertNull(office.rooms)
        assertNull(office.furnitureType)
        assertNull(office.parkings)
        assertEquals(40, office.area)
        assertTrue(office.trackAccessibleByCar)
        assertFalse(office.roadPaved)

        val room = COMPLETE.copy(propertyType = "ROOM").toCreateMandateRequest("XAF")
        assertNull(room.rooms)
        assertNull(room.parkings)
        assertEquals(FurnitureType.UNFURNISHED, room.furnitureType)
    }
}
