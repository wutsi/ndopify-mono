package com.wutsi.ndopify.platform.tenant.servlet

import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.security.server.service.AccessTokenService
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import java.io.IOException

/**
 * Resolves the tenant id from the JWT claim when present, falling back to the [HttpHeader.TENANT_ID] header
 * otherwise. The header fallback is a deliberately weaker trust signal than the JWT claim (unsigned, same as
 * the claim itself today) — it exists only to keep the header-based tenant convention already documented in
 * CLAUDE.md working until real JWT signing is in place.
 */
class TenantContextFilter(private val accessTokenService: AccessTokenService) : Filter {
    @Throws(IOException::class, ServletException::class)
    override fun doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse, filterChain: FilterChain) {
        try {
            TenantContext.set(resolveTenantId(servletRequest))
            filterChain.doFilter(servletRequest, servletResponse)
        } finally {
            TenantContext.remove()
        }
    }

    private fun resolveTenantId(servletRequest: ServletRequest): Long? {
        return resolveFromPrincipal() ?: resolveFromHeader(servletRequest)
    }

    private fun resolveFromPrincipal(): Long? {
        return try {
            accessTokenService.getPrincipalOrNull()?.getTenantId()
        } catch (ex: Exception) {
            null
        }
    }

    private fun resolveFromHeader(servletRequest: ServletRequest): Long? {
        if (servletRequest !is HttpServletRequest) return null
        return servletRequest.getHeader(HttpHeader.TENANT_ID)?.toLongOrNull()
    }
}
