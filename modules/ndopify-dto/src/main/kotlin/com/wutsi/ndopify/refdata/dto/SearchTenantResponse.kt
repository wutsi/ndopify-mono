package com.wutsi.ndopify.refdata.dto

data class SearchTenantResponse(
    val tenants: List<Tenant> = emptyList()
)
