package com.wutsi.ndopify.npc.config

import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.web.core.client.ndopify.NdTenantClient
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantPortal
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import com.wutsi.ndopify.web.core.service.security.JwtDecoder
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.Clock

/**
 * The `ndopify-web-core` beans, for Partner Central: the tenant found by its `partnerCentralUrl`, the
 * `__npc_access_token` session cookie and only `partner-central` tokens.
 */
@Configuration
@Import(NdTenantClient::class)
class WebCoreConfiguration(private val clock: Clock) {
    companion object {
        const val ACCESS_TOKEN_COOKIE = "__npc_access_token"
    }

    @Bean
    fun tenantContext(api: NdTenantClient, request: HttpServletRequest): TenantContext =
        TenantContext(api, request, TenantPortal.PARTNER_CENTRAL)

    @Bean
    fun accessTokenService(request: HttpServletRequest, response: HttpServletResponse): AccessTokenService =
        AccessTokenService(request, response, ACCESS_TOKEN_COOKIE, true, clock)

    @Bean
    fun jwtDecoder(): JwtDecoder = JwtDecoder(ApplicationCode.PARTNER_CENTRAL)
}
