package com.wutsi.ndopify.refdata.dto

data class SearchNearestLocationResponse(
    val locations: List<NearestLocation> = emptyList()
)
