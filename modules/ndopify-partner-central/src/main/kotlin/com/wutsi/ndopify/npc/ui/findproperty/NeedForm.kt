package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.koki.listing.dto.PropertyCategory

/** Step "need". */
data class NeedForm(
    val propertyCategory: PropertyCategory? = null,
)
