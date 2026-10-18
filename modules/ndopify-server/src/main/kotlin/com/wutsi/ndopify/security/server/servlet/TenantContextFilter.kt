package com.wutsi.ndopify.security.server.servlet

import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.server.service.AccessTokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Resolves the tenant id from the JWT claim when present, falling back to the [HttpHeader.TENANT_ID] header
 * otherwise. The header fallback is a deliberately weaker trust signal than the JWT claim (unsigned, same as
 * the claim itself today) — it exists only to keep the header-based tenant convention already documented in
 * CLAUDE.md working until real JWT signing is in place.
 */
class TenantContextFilter(
    private val accessTokenService: AccessTokenService,
    private val tenantService: TenantService,
) : OncePerRequestFilter() {
    companion object {
        // Global reference data: not tenant-scoped, called before a tenant is known (ex: Partner Central's GET /v1/tenants)
        private val EXCLUDED_PATHS = listOf(
            "/v1/tenants",
            "/v1/locations",
            "/v1/applications",
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI.removePrefix(request.contextPath)
        return EXCLUDED_PATHS.any { path == it || path.startsWith("$it/") }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val tenant = resolveTenant(request)
            TenantContext.set(tenant.id)
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.remove()
        }
    }

    private fun resolveTenant(request: HttpServletRequest): TenantEntity {
        val id = resolveFromPrincipal()
            ?: resolveFromHeader(request)
            ?: throw BadRequestException(Error(code = ErrorCode.TENANT_MISSING_HEADER))

        val tenant = tenantService.findByIdOrNull(id)
            ?: throw BadRequestException(Error(code = ErrorCode.TENANT_ID_NOT_VALID))

        if (tenant.active == false) {
            throw BadRequestException(Error(code = ErrorCode.TENANT_NOT_ACTIVE))
        }

        return tenant
    }

    private fun resolveFromPrincipal(): Long? {
        return try {
            accessTokenService.getPrincipalOrNull()?.getTenantId()
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveFromHeader(request: HttpServletRequest): Long? {
        return request.getHeader(HttpHeader.TENANT_ID)?.toLongOrNull()
    }
}
