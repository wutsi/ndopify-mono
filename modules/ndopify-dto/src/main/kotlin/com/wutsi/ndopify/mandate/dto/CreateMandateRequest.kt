package com.wutsi.ndopify.mandate.dto

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.refdata.dto.Money
import jakarta.validation.constraints.NotEmpty

data class CreateMandateRequest(
    val propertyCategory: PropertyCategory = PropertyCategory.UNKNOWN,
    val mandateType: MandateType = MandateType.UNKNOWN,
    val listingType: ListingType = ListingType.UNKNOWN,

    val cityId: Long = -1,
    val neighbourhoodIds: List<Long> = emptyList(),

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

    @get:NotEmpty val firstName: String = "",
    @get:NotEmpty val lastName: String = "",
    @get:NotEmpty val email: String = "",
    @get:NotEmpty val whatsappNumber: String = "",
    val preferredContactPeriod: ContactPeriodType? = null,

    val comments: String? = null,
)
