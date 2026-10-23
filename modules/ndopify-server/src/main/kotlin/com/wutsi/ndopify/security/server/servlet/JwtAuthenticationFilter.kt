package com.wutsi.ndopify.security.server.servlet

import com.wutsi.ndopify.security.server.service.AccessTokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates the request with its `Authorization: Bearer` token, for Spring Security's `authenticated()` rule.
 * A token that doesn't decode leaves the request anonymous: a protected endpoint then answers 401, and so does a public
 * one, from [TenantContextFilter].
 */
class JwtAuthenticationFilter(
    private val accessTokenService: AccessTokenService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val accessToken = request.getHeader(HttpHeaders.AUTHORIZATION)?.removePrefix("Bearer ")
        val principal = accessToken?.let {
            try {
                accessTokenService.decode(it)
            } catch (e: Exception) {
                null
            }
        }
        if (principal != null) {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, emptyList())
        }
        filterChain.doFilter(request, response)
    }
}
