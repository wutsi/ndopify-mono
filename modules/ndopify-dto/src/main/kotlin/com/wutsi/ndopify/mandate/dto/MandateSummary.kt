package com.wutsi.ndopify.mandate.dto

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.refdata.dto.Money
import java.time.LocalDate
import java.util.Date

/**
 * A mandate in a search: what a card shows (criteria, amenities, moving date), without its terms nor the customer's
 * comments, which only [Mandate] has. Its contract is only referenced ([contractId]): a client needing the contract's
 * dates searches the contracts by id, in one call.
 */
data class MandateSummary(
    val id: Long = -1,
    val customerId: Long = -1,
    val agentId: Long? = null,
    val status: MandateStatus = MandateStatus.UNKNOWN,
    val mandateType: MandateType = MandateType.UNKNOWN,
    val listingType: ListingType = ListingType.UNKNOWN,
    val propertyCategory: PropertyCategory = PropertyCategory.UNKNOWN,
    val propertyType: PropertyType? = null,
    val cityId: Long = -1,
    val neighborhoodIds: List<Long> = emptyList(),
    val furnitureType: FurnitureType? = null,
    val rooms: String? = null,
    val parkings: String? = null,
    val area: Int? = null,

    val waterMeter: Boolean = false,
    val dedicatedWaterMeter: Boolean = false,
    val waterTank: Boolean = false,
    val waterWell: Boolean = false,

    val electricityMeter: Boolean = false,
    val dedicatedElectricityMeter: Boolean = false,
    val generatorSet: Boolean = false,
    val solarPanel: Boolean = false,

    val roadPaved: Boolean = false,
    val trackAccessibleByCar: Boolean = false,
    val trackInaccessibleByCar: Boolean = false,

    val securedFenced: Boolean = false,
    val securityGuard: Boolean = false,
    val videoSurveillance: Boolean = false,

    val price: Money = Money(),
    val movingPeriod: MovingPeriod = MovingPeriod.UNKNOWN,
    val availableFrom: LocalDate? = null,

    val contractId: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
