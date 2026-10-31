package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateSummary
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.time.LocalDate
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MandateSummariesTest {
    companion object {
        /** The search's summary of [mandate]. */
        fun summary(mandate: Mandate) =
            MandateSummary(
                id = mandate.id,
                customerId = mandate.customerId,
                agentId = mandate.agentId,
                status = mandate.status,
                mandateType = mandate.mandateType,
                listingType = mandate.listingType,
                propertyCategory = mandate.propertyCategory,
                propertyType = mandate.propertyType,
                cityId = mandate.cityId,
                neighborhoodIds = mandate.neighborhoodIds,
                furnitureType = mandate.furnitureType,
                rooms = mandate.rooms,
                parkings = mandate.parkings,
                area = mandate.area,
                waterMeter = mandate.waterMeter,
                dedicatedWaterMeter = mandate.dedicatedWaterMeter,
                waterTank = mandate.waterTank,
                waterWell = mandate.waterWell,
                electricityMeter = mandate.electricityMeter,
                dedicatedElectricityMeter = mandate.dedicatedElectricityMeter,
                generatorSet = mandate.generatorSet,
                solarPanel = mandate.solarPanel,
                roadPaved = mandate.roadPaved,
                trackAccessibleByCar = mandate.trackAccessibleByCar,
                trackInaccessibleByCar = mandate.trackInaccessibleByCar,
                securedFenced = mandate.securedFenced,
                securityGuard = mandate.securityGuard,
                videoSurveillance = mandate.videoSurveillance,
                price = mandate.price,
                movingPeriod = mandate.movingPeriod,
                availableFrom = mandate.availableFrom,
                contractId = mandate.contractId,
                createdAt = mandate.createdAt,
                modifiedAt = mandate.modifiedAt,
            )

        // Every field a summary has, none left to its default
        private val MANDATE = Mandate(
            id = 1,
            customerId = 2,
            agentId = 3,
            contractId = "c-1",
            propertyCategory = PropertyCategory.RESIDENTIAL,
            status = MandateStatus.APPROVED,
            mandateType = MandateType.LISTING,
            listingType = ListingType.RENTAL,
            cityId = 4,
            neighborhoodIds = listOf(5, 6),
            propertyType = PropertyType.APARTMENT,
            furnitureType = FurnitureType.FULLY_FURNISHED,
            rooms = "3",
            parkings = "1",
            area = 90,
            waterMeter = true,
            dedicatedWaterMeter = true,
            waterTank = true,
            waterWell = true,
            electricityMeter = true,
            dedicatedElectricityMeter = true,
            generatorSet = true,
            solarPanel = true,
            roadPaved = true,
            trackAccessibleByCar = true,
            trackInaccessibleByCar = true,
            securedFenced = true,
            securityGuard = true,
            videoSurveillance = true,
            price = Money(amount = 150000, currency = "XAF", periodicity = PeriodicityType.MONTHLY),
            movingPeriod = MovingPeriod.WITHIN_1_MONTH,
            availableFrom = LocalDate.of(2026, 11, 3),
            createdAt = Date(1000),
            modifiedAt = Date(2000),
        )
    }

    @Test
    fun `every field of the summary`() {
        assertEquals(MANDATE, MandateSummaries.toMandate(summary(MANDATE)))
    }

    @Test
    fun `without contract`() {
        assertNull(MandateSummaries.toMandate(summary(MANDATE.copy(contractId = null))).contractId)
    }

    @Test
    fun `what a summary lacks is left to its default`() {
        val mandate = MandateSummaries.toMandate(summary(MANDATE))

        assertNull(mandate.comments)
        assertNull(mandate.securityDeposit)
        assertEquals(0, mandate.version)
    }
}
