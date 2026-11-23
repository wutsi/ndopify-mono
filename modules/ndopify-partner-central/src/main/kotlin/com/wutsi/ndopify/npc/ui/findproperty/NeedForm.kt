package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory

/** Step "need": the project (rent or buy) and its use. */
data class NeedForm(
    val listingType: ListingType? = null,
    val propertyCategory: PropertyCategory? = null,
)
