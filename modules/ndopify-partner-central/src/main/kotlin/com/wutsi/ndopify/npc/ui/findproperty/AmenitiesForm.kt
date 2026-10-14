package com.wutsi.ndopify.npc.ui.findproperty

/** Step "amenities": the names of the checked boxes (see [PendingMandate.AMENITIES]). */
data class AmenitiesForm(
    val amenities: List<String> = emptyList(),
)
