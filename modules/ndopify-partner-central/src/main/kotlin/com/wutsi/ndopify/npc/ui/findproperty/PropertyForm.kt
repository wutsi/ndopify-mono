package com.wutsi.ndopify.npc.ui.findproperty

/** Step "property". The area is text: it may come with thousands separators ("1 200"). */
data class PropertyForm(
    val propertyType: String? = null,
    val rooms: String? = null,
    val furnitureType: String? = null,
    val parkings: String? = null,
    val area: String? = null,
)
