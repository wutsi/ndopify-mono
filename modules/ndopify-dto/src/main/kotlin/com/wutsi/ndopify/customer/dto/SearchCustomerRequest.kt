package com.wutsi.ndopify.customer.dto

import org.springframework.web.bind.annotation.BindParam

data class SearchCustomerRequest(
    @param:BindParam("id") val ids: List<Long> = emptyList(),
    @param:BindParam("party-id") val partyIds: List<Long> = emptyList(),

    val limit: Int = 20,
    val offset: Int = 0,
)
