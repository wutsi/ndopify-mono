package com.wutsi.ndopify.refdata.dto

import org.springframework.web.bind.annotation.BindParam

data class SearchNearestLocationRequest(
    @param:BindParam("id") val ids: List<Long> = emptyList(), // the locations to search around
    val type: LocationType? = null, // defaults to the type(s) of the locations searched around
    @param:BindParam("parent-id") val parentId: Long? = null, // only the children of this location (ex: a city)
    val radius: Double = 1.0, // in km
    val limit: Int = 20,
    val offset: Int = 0,
)
