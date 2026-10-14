package com.wutsi.ndopify.refdata.dto

data class NearestLocation(
    val location: Location = Location(),
    val distance: Double = 0.0, // in km
)
