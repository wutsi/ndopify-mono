package com.wutsi.ndopify.party.dto

data class SearchPartyRequest(
    val id: List<Long> = emptyList(),
    val email: String? = null,

    val limit: Int = 20,
    val offset: Int = 0,
)
