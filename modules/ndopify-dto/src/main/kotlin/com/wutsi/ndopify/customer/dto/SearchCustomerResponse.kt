package com.wutsi.ndopify.customer.dto

data class SearchCustomerResponse(
    val customers: List<CustomerSummary> = emptyList()
)
