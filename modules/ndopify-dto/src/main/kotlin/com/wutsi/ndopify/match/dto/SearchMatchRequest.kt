package com.wutsi.ndopify.match.dto

import org.springframework.web.bind.annotation.BindParam

data class SearchMatchRequest(
    @param:BindParam("mandate-id") val mandateIds: List<Long> = emptyList(), // required: at least one
    @param:BindParam("status") val statuses: List<MatchStatus> = emptyList(), // empty: ACTIVE

    val limit: Int = 20,
    val offset: Int = 0,
)
