package com.wutsi.ndopify.npc.ui.join

/** Step "area". */
data class SectorForm(
    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),
)
