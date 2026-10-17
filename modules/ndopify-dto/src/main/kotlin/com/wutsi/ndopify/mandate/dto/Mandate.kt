package com.wutsi.ndopify.mandate.dto

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.refdata.dto.Money
import java.util.Date

data class Mandate(
    val id: Long = -1,
    val customerId: Long = -1,
    val agentId: Long? = null,
    val contractId: String? = null,

    val propertyCategory: PropertyCategory = PropertyCategory.UNKNOWN,
    val status: MandateStatus = MandateStatus.UNKNOWN,
    val mandateType: MandateType = MandateType.UNKNOWN,
    val listingType: ListingType = ListingType.UNKNOWN,

    val cityId: Long = -1,
    val neighborhoodIds: List<Long> = emptyList(),

    val propertyType: PropertyType? = null,
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
    val securityDeposit: Int? = null,
    val advanceRent: Int? = null,
    val leaseTerm: Int? = null,
    val noticePeriod: Int? = null,
    val movingPeriod: MovingPeriod = MovingPeriod.UNKNOWN,

    val preferredContactPeriod: ContactPeriodType? = null,

    val comments: String? = null,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
    val approvedAt: Date? = null,
    val closedAt: Date? = null,

    val age: Int? = null, // Age in days - age on the market from Approval to Closing.
    val finalPrice: Long? = null,
    val cancellationCode: MandateCancellationCode? = null,
    val cancellationReason: String? = null,
)
