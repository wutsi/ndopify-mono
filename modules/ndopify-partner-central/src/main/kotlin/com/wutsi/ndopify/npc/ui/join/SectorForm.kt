package com.wutsi.ndopify.npc.ui.join

/** Step "secteur". */
data class SectorForm(
    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),
)
