package com.wutsi.ndopify.mandate.dto

import com.wutsi.koki.listing.dto.ListingType
import com.wutsi.koki.listing.dto.PropertyType
import com.wutsi.koki.listing.dto.RoadAccessType
import com.wutsi.ndopify.listing.dto.ElectricityAccessType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.WaterAccessType

data class Mandate(
    val id: Long = -1,
    val customerId: Long = -1,
    val agentId: Long? = null,
    val status: MandateStatus = MandateStatus.UNKNOWN,
    val mandateType: MandateType = MandateType.UNKNOWN,
    val listingType: ListingType = ListingType.UNKNOWN,
    val cityId: Long = -1,
    val neighborhoodIds: List<Long> = emptyList(),
    val propertyTypes: List<PropertyType> = emptyList(),
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
    val currencyCode: String = "",
    val securityDeposit: Int? = null,
    val advanceRent: Int? = null,
    val leaseTerm: Int? = null,
    val noticePeriod: Int? = null,
)
