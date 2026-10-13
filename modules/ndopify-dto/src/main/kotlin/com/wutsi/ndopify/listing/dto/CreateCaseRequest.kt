package com.wutsi.ndopify.listing.dto

import com.wutsi.koki.listing.dto.ListingType
import com.wutsi.koki.listing.dto.PropertyType
import com.wutsi.koki.listing.dto.RoadAccessType
import jakarta.validation.constraints.NotEmpty

data class CreateCaseRequest(
    val listingType: ListingType = ListingType.UNKNOWN,

    val budget: Long = -1,

    val cityId: Long = -1,
    val neighbourhoodIds: List<Long> = emptyList(),
    
    val propertyType: PropertyType = PropertyType.UNKNOWN,
    val furnitureType: FurnitureType = FurnitureType.UNKNOWN,
    val rooms: String? = null,
    val parkings: String? = null,
    val minArea: Int? = null,
    val maxArea: Int? = null,

    val waterAccessType: WaterAccessType = WaterAccessType.UNKNOWN,
    val waterTank: Boolean = false,
    val waterWell: Boolean = false,

    val electricityAccessType: ElectricityAccessType = ElectricityAccessType.UNKNOWN,
    val generatorSet: Boolean = false,
    val solarPanel: Boolean = false,

    val roadAccessType: RoadAccessType = RoadAccessType.UNKNOWN,

    val securedFenced: Boolean = false,
    val securityGuard: Boolean = false,
    val videoSurveillance: Boolean = false,

    @get:NotEmpty val firstName: String = "",
    @get:NotEmpty val lastName: String = "",
    @get:NotEmpty val email: String = "",
    @get:NotEmpty val mobileMoneyNumber: String = "",
    val preferredContactPeriod: ContactPeriodType = ContactPeriodType.UNKNOWN,
)
