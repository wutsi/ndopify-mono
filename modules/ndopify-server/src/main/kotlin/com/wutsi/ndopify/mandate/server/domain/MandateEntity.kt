package com.wutsi.ndopify.mandate.dto

import com.wutsi.koki.listing.dto.ListingType
import com.wutsi.koki.listing.dto.PropertyType
import com.wutsi.koki.listing.dto.RoadAccessType
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.ElectricityAccessType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.WaterAccessType
import jakarta.validation.constraints.NotEmpty

data class CreateMandateRequest(
    val mandateType: MandateType = MandateType.UNKNOWN,
    val listingType: ListingType = ListingType.UNKNOWN,

    val cityId: Long = -1,
    val neighbourhoodIds: List<Long> = emptyList(),

    val propertyType: PropertyType? = null,
    val furnitureType: FurnitureType? = null,
    val rooms: String? = null,
    val parkings: String? = null,
    val minArea: Int? = null,
    val maxArea: Int? = null,

    val waterAccessType: WaterAccessType? = null,
    val waterTank: Boolean = false,
    val waterWell: Boolean = false,

    val electricityAccessType: ElectricityAccessType? = null,
    val generatorSet: Boolean = false,
    val solarPanel: Boolean = false,

    val roadAccessType: RoadAccessType? = null,

    val securedFenced: Boolean = false,
    val securityGuard: Boolean = false,
    val videoSurveillance: Boolean = false,

    val price: Long = -1,

    @get:NotEmpty val firstName: String = "",
    @get:NotEmpty val lastName: String = "",
    @get:NotEmpty val email: String = "",
    @get:NotEmpty val mobileMoneyNumber: String = "",
    val preferredContactPeriod: ContactPeriodType = ContactPeriodType.UNKNOWN,
)
