package com.wutsi.ndopify.listing.dto

data class SearchListingStatsResponse(
    val stats: List<ListingStats> = emptyList(),
)
