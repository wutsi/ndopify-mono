package com.wutsi.ndopify.web.core.service.security

import com.auth0.jwt.exceptions.JWTVerificationException
import com.wutsi.ndopify.web.core.service.security.AccessTokenService.Companion.ACCESS_TOKEN_REJECTED
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates the request from the access-token cookie. A token that doesn't decode (expired, malformed) leaves
 * the request signed out, clears the cookie, and marks the request [ACCESS_TOKEN_REJECTED] so that it isn't sent to
 * ndopify-server meanwhile: otherwise every call, even the sign-in's, would get a 401.
 */
class JwtAuthenticationFilter(
    private val accessTokenHolder: AccessTokenService,
    private val jwtDecoder: JwtDecoder,
) : OncePerRequestFilter() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val accessToken = accessTokenHolder.get()
            if (accessToken == null) {
                SecurityContextHolder.clearContext()
            } else {
                val principal = jwtDecoder.decode(accessToken)
                val auth = JwtAuthentication(principal)
                auth.isAuthenticated = true
                SecurityContextHolder.setContext(SecurityContextImpl(auth))
            }
        } catch (ex: JWTVerificationException) {
            LOGGER.warn("Token error", ex)
            SecurityContextHolder.clearContext()
            request.setAttribute(ACCESS_TOKEN_REJECTED, true)
            accessTokenHolder.remove()
        } finally {
            filterChain.doFilter(request, response)
        }
    }
}
