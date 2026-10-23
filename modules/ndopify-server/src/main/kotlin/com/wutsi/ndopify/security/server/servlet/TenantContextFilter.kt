package com.wutsi.ndopify.security.server.servlet

import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.error.server.exception.UnauthorizedException
import com.wutsi.ndopify.error.server.exception.WutsiException
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.server.service.AccessTokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Resolves the tenant id from the JWT claim when present. Without a token, only a [PublicEndpoints.TENANT] endpoint
 * (called before the caller has one: OTP, sign-in, registration...) takes it from the [HttpHeader.TENANT_ID] header;
 * any other is a 401 `AUTH_UNAUTHORIZED`, so that the header never opens a tenant's data. With a token, a header naming
 * another tenant is a 403 `AUTH_ACCESS_DENIED`.
 *
 * A request without a valid tenant is rejected here, before the DispatcherServlet: the error goes through
 * [exceptionResolver] (Spring MVC's, so `ControllerErrorHandler`) to get the same [ErrorResponse] as an endpoint's.
 * So is a request whose token doesn't decode (expired, malformed): 401 `AUTH_UNAUTHORIZED`, so that the client
 * knows to sign in again, even on a public endpoint.
 */
class TenantContextFilter(
    private val accessTokenService: AccessTokenService,
    private val tenantService: TenantService,
    private val exceptionResolver: HandlerExceptionResolver,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = PublicEndpoints.isGlobal(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val tenant = try {
            resolveTenant(request)
        } catch (e: WutsiException) {
            exceptionResolver.resolveException(request, response, null, e) ?: throw e
            return
        }

        try {
            TenantContext.set(tenant.id)
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.remove()
        }
    }

    private fun resolveTenant(request: HttpServletRequest): TenantEntity {
        val headerTenantId = resolveFromHeader(request)
        val id = resolveFromPrincipal()?.also { principalTenantId ->
            if (headerTenantId != null && headerTenantId != principalTenantId) {
                throw ForbiddenException(Error(code = ErrorCode.AUTH_ACCESS_DENIED))
            }
        } ?: if (PublicEndpoints.isTenant(request)) {
            headerTenantId ?: throw BadRequestException(Error(code = ErrorCode.TENANT_MISSING_HEADER))
        } else {
            // A token without tenant (never issued: signing in requires one) is no better than none
            throw UnauthorizedException(Error(code = ErrorCode.AUTH_UNAUTHORIZED))
        }

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
        } catch (e: ForbiddenException) {
            // The token doesn't decode: not a forbidden caller but one to sign in again. The JWT library's message
            // (ex: the expiry date) stays out of the response.
            throw UnauthorizedException(Error(code = ErrorCode.AUTH_UNAUTHORIZED), e)
        }
    }

    private fun resolveFromHeader(request: HttpServletRequest): Long? {
        return request.getHeader(HttpHeader.TENANT_ID)?.toLongOrNull()
    }
}
