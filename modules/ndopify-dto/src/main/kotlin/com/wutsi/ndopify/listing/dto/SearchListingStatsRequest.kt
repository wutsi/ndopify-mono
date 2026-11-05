package com.wutsi.ndopify.listing.dto

import org.springframework.web.bind.annotation.BindParam

data class SearchListingStatsRequest(
    @param:BindParam("listing-id") val listingIds: List<Long> = emptyList(),
)
