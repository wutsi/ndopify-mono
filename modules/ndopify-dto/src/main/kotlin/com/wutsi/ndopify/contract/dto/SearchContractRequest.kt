package com.wutsi.ndopify.contract.dto

import org.springframework.web.bind.annotation.BindParam

data class SearchContractRequest(
    @param:BindParam("id") val ids: List<String> = emptyList(),
    @param:BindParam("status") val statuses: List<ContractStatus> = emptyList(),
    @param:BindParam("party-id") val partyIds: List<Long> = emptyList(),
    @param:BindParam("contract-party-id") val contractPartyIds: List<String> = emptyList(),

    val limit: Int = 20,
    val offset: Int = 0,
)
