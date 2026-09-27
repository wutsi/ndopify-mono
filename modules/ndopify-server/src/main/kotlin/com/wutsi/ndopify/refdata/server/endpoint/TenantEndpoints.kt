package com.wutsi.ndopify.refdata.server.endpoint

import com.wutsi.ndopify.refdata.dto.SearchTenantResponse
import com.wutsi.ndopify.refdata.server.mapper.TenantMapper
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/tenants")
class TenantEndpoints(
    private val service: TenantService,
    private val mapper: TenantMapper,
) {
    @GetMapping
    fun search(
        @RequestParam(required = false) active: Boolean? = null
    ): SearchTenantResponse {
        val tenants = service.search(active)
        return SearchTenantResponse(
            tenants = tenants.map { tenant -> mapper.toTenant(tenant) }
        )
    }
}
