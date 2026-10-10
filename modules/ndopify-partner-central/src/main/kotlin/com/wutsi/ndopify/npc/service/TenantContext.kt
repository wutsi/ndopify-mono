package com.wutsi.ndopify.npc.service

import com.wutsi.ndopify.npc.client.ndopify.NdTenantClient
import com.wutsi.ndopify.refdata.dto.Tenant
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Service

@Service
class TenantContext(
    private val api: NdTenantClient,
    private val request: HttpServletRequest,
) {
    private lateinit var tenants: List<Tenant>

    fun getTenantId(): Long {
        val requestUrl = request.requestURL.toString()
        val tenants = all()
        return tenants.firstOrNull { tenant ->
            tenant.partnerCentralUrl?.let { url -> requestUrl.startsWith(url) } ?: false
        }?.id
            ?: throw IllegalStateException("No tenant found for URL: $requestUrl")
    }

    fun all(): List<Tenant> {
        if (!::tenants.isInitialized) {
            tenants = api.search().tenants
        }
        return tenants
    }
}
