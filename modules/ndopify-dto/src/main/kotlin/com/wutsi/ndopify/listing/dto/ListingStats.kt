package com.wutsi.ndopify.listing.dto

data class ListingStats(
    val listingId: Long = -1,
    val interested: Int = 0,
    val upcomingVisits: Int = 0,
    val completedVisits: Int = 0,
)
