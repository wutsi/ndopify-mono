package com.wutsi.ndopify.listing.dto

import java.util.Date

data class ListingSectionSummary(
    val section: ListingSection = ListingSection.UNKNOWN,
    val status: ListingSectionStatus = ListingSectionStatus.UNKNOWN,

    // An optional section (REMARKS) isn't needed to send the listing
    val required: Boolean = true,

    // When the agent last saved it (null for PHOTOS: verified once there are enough photos, none rejected)
    val verifiedAt: Date? = null,
)
