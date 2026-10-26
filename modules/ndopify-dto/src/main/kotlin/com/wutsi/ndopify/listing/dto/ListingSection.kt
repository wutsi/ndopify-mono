package com.wutsi.ndopify.listing.dto

/** A section of a listing's checklist: what the agent checks on site, one form each. In the checklist's order. */
enum class ListingSection {
    UNKNOWN,
    LOCATION,
    GPS,
    PROPERTY,
    AMENITIES,
    TERMS,
    PHOTOS,
    REMARKS,
}
