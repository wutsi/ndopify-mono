package com.wutsi.ndopify.refdata.dto

data class Location(
    val id: Long = -1,
    val parentId: Long? = null,
    val name: String = "",
    val type: LocationType = LocationType.UNKNOWN,
    val country: String = "",
    val population: Long = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
