package com.wutsi.ndopify.match.dto

import org.springframework.web.bind.annotation.BindParam

data class SearchMatchRequest(
    @param:BindParam("mandate-id") val mandateIds: List<Long> = emptyList(), // required: at least one, or a listing-id
    @param:BindParam("listing-id") val listingIds: List<Long> = emptyList(), // Ex: who a published listing interests
    @param:BindParam("status") val statuses: List<MatchStatus> = emptyList(), // empty: ACTIVE

    val limit: Int = 20,
    val offset: Int = 0,
)
