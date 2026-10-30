package com.wutsi.ndopify.web.config

import com.wutsi.ndopify.web.core.client.ndopify.NdTenantClient
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantPortal
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * The `ndopify-web-core` beans, for the public site: the tenant found by its `publicPortalUrl`. No access-token or
 * JWT beans: nobody signs in here.
 */
@Configuration
@Import(NdTenantClient::class)
class WebCoreConfiguration {
    @Bean
    fun tenantContext(api: NdTenantClient, request: HttpServletRequest): TenantContext =
        TenantContext(api, request, TenantPortal.PUBLIC_PORTAL)
}
