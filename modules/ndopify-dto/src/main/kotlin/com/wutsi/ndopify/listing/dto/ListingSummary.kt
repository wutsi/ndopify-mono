package com.wutsi.ndopify.listing.dto

import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.util.Date

data class ListingSummary(
    val id: Long = -1,
    val mandateId: Long = -1,
    val agentId: Long = -1,
    val heroImageId: Long? = null,

    val propertyCategory: PropertyCategory = PropertyCategory.UNKNOWN,
    val listingType: ListingType = ListingType.UNKNOWN,
    val status: ListingStatus = ListingStatus.UNKNOWN,

    val cityId: Long = -1,
    val neighborhoodId: Long? = null,

    val propertyType: PropertyType? = null,
    val furnitureType: FurnitureType? = null,
    val rooms: Int? = null,
    val bathrooms: Int? = null,
    val area: Int? = null,

    val price: Long = -1,
    val currencyCode: String = "",
    val pricePeriodicityType: PeriodicityType = PeriodicityType.UNKNOWN,

    val title: String? = null,
    val summary: String? = null,
    val url: String? = null,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
    val publishedAt: Date? = null,
    val closedAt: Date? = null,
)
