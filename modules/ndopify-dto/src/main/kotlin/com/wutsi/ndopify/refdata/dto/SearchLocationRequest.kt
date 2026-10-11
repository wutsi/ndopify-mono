package com.wutsi.ndopify.refdata.dto

import org.springframework.web.bind.annotation.BindParam

data class SearchLocationRequest(
    @param:BindParam("q") val keyword: String? = null,
    @param:BindParam("id") val ids: List<Long> = emptyList(),
    @param:BindParam("parent-id") val parentId: Long? = null,
    @param:BindParam("type") val types: List<LocationType> = emptyList(),
    val country: String? = null,

    val limit: Int = 20,
    val offset: Int = 0,
)
