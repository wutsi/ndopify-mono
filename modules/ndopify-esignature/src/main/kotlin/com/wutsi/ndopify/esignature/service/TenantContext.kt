package com.wutsi.ndopify.esignature.service

import com.wutsi.ndopify.esignature.client.ndopify.NdTenantClient
import com.wutsi.ndopify.refdata.dto.Tenant
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TenantContext(
    private val api: NdTenantClient,
    private val request: HttpServletRequest,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TenantContext::class.java)
    }

    private lateinit var tenants: List<Tenant>

    fun getTenantId(): Long = getTenant().id

    fun getTenant(): Tenant {
        val requestUrl = request.requestURL.toString()
        val tenants = all()
        return tenants.firstOrNull { tenant ->
            tenant.eSignaturePortalUrl?.let { url -> requestUrl.startsWith(url) } ?: false
        }
            ?: throw IllegalStateException("No tenant found for URL: $requestUrl")
    }

    fun all(): List<Tenant> {
        if (!::tenants.isInitialized) {
            LOGGER.info(">>> Loading tenants from server")
            tenants = api.search().tenants
            LOGGER.info(">>> ${tenants.size} tenants loaded")
        }
        return tenants
    }
}
