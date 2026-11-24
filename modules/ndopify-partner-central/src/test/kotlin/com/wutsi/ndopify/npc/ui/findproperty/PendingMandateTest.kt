package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.FinancingType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingMandateTest {
    companion object {
        val COMPLETE = PendingMandate(
            propertyCategory = PropertyCategory.RESIDENTIAL,
            listingType = ListingType.RENTAL,
            cityId = 2220957,
            neighborhoodIds = listOf(1001, 1002),
            propertyType = "APARTMENT",
            rooms = "3",
            furnitureType = "ANY",
            parkings = "1",
            area = 80,
            amenities = setOf("waterMeter", "securityGuard"),
            priceAmount = 150_000,
            movingPeriod = MovingPeriod.WITHIN_1_MONTH,
            firstName = "Ray",
            lastName = "Sponsible",
            email = "ray@gmail.com",
            whatsappNumber = "+237670000001",
            preferredContactPeriod = ContactPeriodType.EVENING,
            comments = "Près d'une école",
        )
    }

    private val buildingSale = COMPLETE.copy(
        listingType = ListingType.SALE,
        propertyCategory = PropertyCategory.COMMERCIAL,
        propertyType = "BUILDING",
        rooms = null,
        furnitureType = null,
        parkings = null,
        levels = 3,
        units = 12,
        priceAmount = 250_000_000,
        financingType = FinancingType.CASH,
    )

    @Test
    fun emptyStartsAtNeed() {
        assertEquals("need", PendingMandate().firstIncompleteStep())
    }

    @Test
    fun `a building sale skips the amenities step`() {
        assertEquals(
            listOf("need", "area", "property", "budget", "contact", "review"),
            buildingSale.steps(),
        )
        assertEquals("review", buildingSale.firstIncompleteStep())
        assertTrue(buildingSale.isComplete("amenities"))
        assertEquals(
            listOf("need", "area", "property", "amenities", "budget", "contact", "review"),
            COMPLETE.steps(),
        )
    }

    @Test
    fun `a building sale stops at the budget once its property is answered`() {
        assertEquals("budget", buildingSale.copy(priceAmount = null).firstIncompleteStep())
    }

    @Test
    fun `a building sale requires both counts unless indifferent`() {
        assertTrue(buildingSale.isComplete("property"))
        assertFalse(buildingSale.copy(levels = null).isComplete("property"))
        assertFalse(buildingSale.copy(units = null).isComplete("property"))
        assertTrue(buildingSale.copy(levels = null, levelsAny = true, units = null, unitsAny = true).isComplete("property"))
        assertEquals("property", buildingSale.copy(units = null).firstIncompleteStep())
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
        assertFalse(request.securityGuard)
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
    fun completeGoesToReview() {
        assertEquals("review", COMPLETE.firstIncompleteStep())
    }

    @Test
    fun amenitiesAreAlwaysComplete() {
        assertTrue(PendingMandate().isComplete("amenities"))
    }

    @Test
    fun unknownStepIsNeverComplete() {
        assertFalse(COMPLETE.isComplete("review"))
    }

    @Test
    fun areaWithoutNeighborhoodsIsComplete() {
        assertTrue(COMPLETE.copy(neighborhoodIds = emptyList()).isComplete("area"))
        assertFalse(COMPLETE.copy(cityId = null, neighborhoodIds = emptyList()).isComplete("area"))
    }

    @Test
    fun missingBudgetStopsAtBudget() {
        assertEquals("budget", COMPLETE.copy(priceAmount = null).firstIncompleteStep())
    }

    @Test
    fun missingContactStopsAtContact() {
        assertEquals("contact", COMPLETE.copy(whatsappNumber = "0670").firstIncompleteStep())
    }

    @Test
    fun roomsRequiredOnlyWhenTheTypeHasRooms() {
        assertFalse(COMPLETE.copy(rooms = null).isComplete("property"))
        assertTrue(COMPLETE.copy(propertyType = "STUDIO", rooms = null).isComplete("property"))
    }

    @Test
    fun furnitureRequiredOnlyWhenTheTypeHasFurniture() {
        assertFalse(COMPLETE.copy(furnitureType = null).isComplete("property"))
        assertTrue(
            COMPLETE.copy(
                propertyCategory = PropertyCategory.COMMERCIAL,
                propertyType = "OFFICE",
                rooms = null,
                furnitureType = null,
            ).isComplete("property"),
        )
    }

    @Test
    fun `a rental land never completes the property step`() {
        val land = COMPLETE.copy(propertyCategory = PropertyCategory.RESIDENTIAL, propertyType = "LAND", rooms = null, furnitureType = null)
        assertFalse(land.isComplete("property"))
    }

    @Test
    fun changingCategoryInvalidatesProperty() {
        val changed = COMPLETE.copy(propertyCategory = PropertyCategory.COMMERCIAL)
        assertEquals("property", changed.firstIncompleteStep())
    }

    @Test
    fun `a rental is the same request as before`() {
        val pending = COMPLETE

        val request = pending.toCreateMandateRequest("XAF")

        assertEquals(ListingType.RENTAL, request.listingType)
        assertEquals(PeriodicityType.MONTHLY, request.price.periodicity)
        assertNull(request.financingType)
        assertEquals(emptyList(), request.titleDocuments)
    }

    @Test
    fun `a land purchase sends its financing and documents at a one-off price`() {
        val pending = COMPLETE.copy(
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = "LAND",
            area = 400,
            priceAmount = 10_000_000,
            financingType = FinancingType.CASH,
            titleDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
            rooms = "3",
            furnitureType = "UNFURNISHED",
        )

        val request = pending.toCreateMandateRequest("XAF")

        assertEquals(ListingType.SALE, request.listingType)
        assertEquals(PeriodicityType.NONE, request.price.periodicity)
        assertEquals(FinancingType.CASH, request.financingType)
        assertEquals(listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN), request.titleDocuments)
        assertNull(request.rooms)
        assertNull(request.furnitureType)
        assertNull(request.securityDeposit)
        assertNull(request.advanceRent)
        assertNull(request.leaseTerm)
        assertNull(request.noticePeriod)
    }

    @Test
    fun `a purchase sends its documents whatever its type, a rental none`() {
        val documents = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.LAND_TITLE, TitleDocumentType.UNKNOWN)
        val purchase = COMPLETE.copy(
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = "HOUSE",
            rooms = null,
            furnitureType = null,
            priceAmount = 10_000_000,
            financingType = FinancingType.CASH,
            titleDocuments = documents,
        )

        assertEquals(listOf(TitleDocumentType.LAND_TITLE), purchase.toCreateMandateRequest("XAF").titleDocuments)
        assertEquals(
            emptyList(),
            purchase.copy(
                listingType = ListingType.RENTAL,
                propertyType = "APARTMENT",
            ).toCreateMandateRequest("XAF").titleDocuments,
        )
        assertEquals(emptyList(), purchase.copy(titleDocuments = emptyList()).toCreateMandateRequest("XAF").titleDocuments)
    }

    @Test
    fun `a land purchase without a document is complete`() {
        val pending = PendingMandateTest.COMPLETE.copy(
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = "LAND",
            area = 400,
            rooms = null,
            furnitureType = null,
            parkings = null,
            priceAmount = 10_000_000,
            financingType = FinancingType.CASH,
            titleDocuments = emptyList(),
        )

        assertTrue(pending.isComplete("property"))
        assertFalse(pending.copy(area = null).isComplete("property"))
    }

    @Test
    fun `a sale requires its financing`() {
        val sale = COMPLETE.copy(listingType = ListingType.SALE, priceAmount = 10_000_000)

        assertFalse(sale.isComplete("budget"))
        assertTrue(sale.copy(financingType = FinancingType.CASH).isComplete("budget"))
    }

    @Test
    fun toCreateMandateRequest() {
        val request = COMPLETE.toCreateMandateRequest("XAF")

        assertEquals(MandateType.SEARCH, request.mandateType)
        assertEquals(ListingType.RENTAL, request.listingType)
        assertEquals(PropertyCategory.RESIDENTIAL, request.propertyCategory)
        assertEquals(2220957L, request.cityId)
        assertEquals(listOf(1001L, 1002L), request.neighbourhoodIds)
        assertEquals(PropertyType.APARTMENT, request.propertyType)
        assertEquals("3", request.rooms)
        assertNull(request.furnitureType) // ANY
        assertEquals("1", request.parkings)
        assertEquals(80, request.area)
        assertTrue(request.waterMeter)
        assertTrue(request.securityGuard)
        assertFalse(request.solarPanel)
        assertEquals(150_000L, request.price.amount)
        assertEquals("XAF", request.price.currency)
        assertEquals(PeriodicityType.MONTHLY, request.price.periodicity)
        assertEquals(MovingPeriod.WITHIN_1_MONTH, request.movingPeriod)
        assertEquals("Ray", request.firstName)
        assertEquals("Sponsible", request.lastName)
        assertEquals("ray@gmail.com", request.email)
        assertEquals("+237670000001", request.whatsappNumber)
        assertEquals(ContactPeriodType.EVENING, request.preferredContactPeriod)
        assertEquals("Près d'une école", request.comments)
    }

    @Test
    fun everyAmenityIsMapped() {
        val request = COMPLETE.copy(amenities = PendingMandate.AMENITIES.toSet()).toCreateMandateRequest("XAF")

        assertTrue(
            listOf(
                request.waterMeter, request.dedicatedWaterMeter, request.waterTank, request.waterWell,
                request.electricityMeter, request.dedicatedElectricityMeter, request.generatorSet, request.solarPanel,
                request.roadPaved, request.trackAccessibleByCar, request.trackInaccessibleByCar,
                request.securedFenced, request.securityGuard, request.videoSurveillance,
            ).all { it },
        )
    }

    @Test
    fun staleRoomsAreNotSent() {
        // Rooms answered for an APARTMENT, then the type changed to STUDIO: rooms no longer applies.
        val request = COMPLETE.copy(propertyType = "STUDIO").toCreateMandateRequest("XAF")
        assertNull(request.rooms)
    }

    @Test
    fun furnitureAndParkingsNotSentForLand() {
        val request = COMPLETE.copy(
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = "LAND",
            area = 400,
            priceAmount = 10_000_000,
            rooms = null,
            furnitureType = "FULLY_FURNISHED",
        ).toCreateMandateRequest("XAF")
        assertNull(request.furnitureType)
        assertNull(request.parkings)
    }

    @Test
    fun anyTypeIsSentAsNull() {
        val request = COMPLETE.copy(propertyType = "ANY").toCreateMandateRequest("XAF")
        assertNull(request.propertyType)
        assertEquals(COMPLETE.rooms, request.rooms)
        assertNull(request.furnitureType)
    }

    @Test
    fun anyParkingIsSentAsNull() {
        assertNull(COMPLETE.copy(parkings = "ANY").toCreateMandateRequest("XAF").parkings)
    }

    @Test
    fun furnished() {
        val request = COMPLETE.copy(furnitureType = "FULLY_FURNISHED").toCreateMandateRequest("XAF")
        assertEquals(FurnitureType.FULLY_FURNISHED, request.furnitureType)
    }

    @Test
    fun blankCommentsAreNull() {
        assertNull(COMPLETE.copy(comments = "").toCreateMandateRequest("XAF").comments)
    }

    @Test
    fun has() {
        assertTrue(COMPLETE.has("waterMeter"))
        assertFalse(COMPLETE.has("solarPanel"))
    }
}
