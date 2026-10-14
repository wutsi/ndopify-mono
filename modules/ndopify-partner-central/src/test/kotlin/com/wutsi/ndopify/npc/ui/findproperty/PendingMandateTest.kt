package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.koki.listing.dto.ListingType
import com.wutsi.koki.listing.dto.PropertyCategory
import com.wutsi.koki.listing.dto.PropertyType
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.mandate.dto.MandateType
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

    @Test
    fun emptyStartsAtNeed() {
        assertEquals("need", PendingMandate().firstIncompleteStep())
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
        assertTrue(COMPLETE.copy(propertyType = "LAND", rooms = null, furnitureType = null).isComplete("property"))
    }

    @Test
    fun changingCategoryInvalidatesProperty() {
        val changed = COMPLETE.copy(propertyCategory = PropertyCategory.COMMERCIAL)
        assertEquals("property", changed.firstIncompleteStep())
    }

    @Test
    fun toCreateMandateRequest() {
        val request = COMPLETE.toCreateMandateRequest("XAF")

        assertEquals(MandateType.TENANT, request.mandateType)
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
        val request = COMPLETE.copy(propertyType = "LAND", rooms = null, furnitureType = "FULLY_FURNISHED")
            .toCreateMandateRequest("XAF")
        assertNull(request.furnitureType)
        assertNull(request.parkings)
    }

    @Test
    fun anyTypeIsSentAsNull() {
        val request = COMPLETE.copy(propertyType = "ANY").toCreateMandateRequest("XAF")
        assertNull(request.propertyType)
        assertNull(request.rooms)
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
