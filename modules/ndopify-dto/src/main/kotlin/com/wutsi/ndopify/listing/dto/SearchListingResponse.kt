package com.wutsi.ndopify.listing.dto

data class SearchListingResponse(
    val listings: List<ListingSummary> = emptyList(),
)
