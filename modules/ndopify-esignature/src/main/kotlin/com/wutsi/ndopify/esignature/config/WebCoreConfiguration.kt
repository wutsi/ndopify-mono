package com.wutsi.ndopify.esignature.config

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
import java.time.Duration

/**
 * The `ndopify-web-core` beans, for the e-signature portal: the tenant found by its `eSignaturePortalUrl`, the
 * `__nes_access_token` cookie (1 hour, the token's TTL on ndopify-server) and only `e-signature` tokens.
 */
@Configuration
@Import(NdTenantClient::class)
class WebCoreConfiguration {
    companion object {
        const val ACCESS_TOKEN_COOKIE = "__nes_access_token"
        private val ACCESS_TOKEN_TTL = Duration.ofHours(1)
    }

    @Bean
    fun tenantContext(api: NdTenantClient, request: HttpServletRequest): TenantContext =
        TenantContext(api, request, TenantPortal.E_SIGNATURE)

    @Bean
    fun accessTokenService(request: HttpServletRequest, response: HttpServletResponse): AccessTokenService =
        AccessTokenService(request, response, ACCESS_TOKEN_COOKIE, ACCESS_TOKEN_TTL)

    @Bean
    fun jwtDecoder(): JwtDecoder = JwtDecoder(ApplicationCode.E_SIGNATURE)
}
