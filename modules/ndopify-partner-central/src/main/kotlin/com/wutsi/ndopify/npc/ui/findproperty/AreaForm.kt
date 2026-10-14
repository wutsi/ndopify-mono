package com.wutsi.ndopify.npc.ui.findproperty

/** Step "area". */
data class AreaForm(
    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),
)
