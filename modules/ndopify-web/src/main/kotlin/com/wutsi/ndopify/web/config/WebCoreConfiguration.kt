package com.wutsi.ndopify.web.config

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
 * The `ndopify-web-core` beans, for the public site: the tenant found by its `publicPortalUrl`, and the
 * `__nweb_access_token` cookie that keeps the `public-portal` token of a match link (only those are accepted). Nobody
 * else signs in here.
 */
@Configuration
@Import(NdTenantClient::class)
class WebCoreConfiguration(private val clock: Clock) {
    companion object {
        const val ACCESS_TOKEN_COOKIE = "__nweb_access_token"
    }

    @Bean
    fun tenantContext(api: NdTenantClient, request: HttpServletRequest): TenantContext =
        TenantContext(api, request, TenantPortal.PUBLIC_PORTAL)

    @Bean
    fun accessTokenService(request: HttpServletRequest, response: HttpServletResponse): AccessTokenService =
        AccessTokenService(request, response, ACCESS_TOKEN_COOKIE, true, clock)

    @Bean
    fun jwtDecoder(): JwtDecoder = JwtDecoder(ApplicationCode.PUBLIC_PORTAL)
}
