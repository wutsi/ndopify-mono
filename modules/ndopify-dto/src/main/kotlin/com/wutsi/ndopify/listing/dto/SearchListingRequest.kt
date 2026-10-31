package com.wutsi.ndopify.listing.dto

import org.springframework.web.bind.annotation.BindParam
import java.time.LocalDate

data class SearchListingRequest(
    @param:BindParam("id") val ids: List<Long> = emptyList(),
    @param:BindParam("mandate-id") val mandateIds: List<Long> = emptyList(),
    @param:BindParam("agent-id") val agentId: Long? = null,
    @param:BindParam("property-category") val propertyCategory: PropertyCategory? = null,
    @param:BindParam("listing-type") val listingType: ListingType? = null,
    @param:BindParam("property-type") val propertyTypes: List<PropertyType> = emptyList(),
    @param:BindParam("status") val statuses: List<ListingStatus> = emptyList(),
    @param:BindParam("city-id") val cityId: Long? = null,
    @param:BindParam("neighborhood-id") val neighborhoodIds: List<Long> = emptyList(),
    @param:BindParam("furniture-type") val furnitureType: FurnitureType? = null,
    @param:BindParam("min-price") val minPrice: Long? = null,
    @param:BindParam("max-price") val maxPrice: Long? = null,
    @param:BindParam("available-from") val availableFrom: LocalDate? = null,

    val rooms: String? = null, // Format: 1, 2, 2+, 3, 3+ etc.

    val limit: Int = 20,
    val offset: Int = 0,
)
