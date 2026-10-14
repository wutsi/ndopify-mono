package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.PropertyCategory

/** Step "need". */
data class NeedForm(
    val propertyCategory: PropertyCategory? = null,
)
