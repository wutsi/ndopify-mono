package com.wutsi.ndopify.contract.dto

data class SearchContractResponse(
    val contracts: List<ContractSummary> = emptyList(),
)
