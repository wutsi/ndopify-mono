package com.wutsi.ndopify.mandate.dto

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.refdata.dto.Money
import java.util.Date

data class MandateSummary(
    val id: Long = -1,
    val customerId: Long = -1,
    val agentId: Long? = null,
    val status: MandateStatus = MandateStatus.UNKNOWN,
    val mandateType: MandateType = MandateType.UNKNOWN,
    val listingType: ListingType = ListingType.UNKNOWN,
    val propertyType: PropertyType? = null,
    val cityId: Long = -1,
    val neighborhoodIds: List<Long> = emptyList(),
    val rooms: String? = null,
    val price: Money = Money(),
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
