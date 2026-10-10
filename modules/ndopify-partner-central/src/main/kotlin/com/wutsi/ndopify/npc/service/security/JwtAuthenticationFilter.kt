package com.wutsi.ndopify.npc.service.security

import com.auth0.jwt.exceptions.TokenExpiredException
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.stereotype.Service

@Service
class JwtAuthenticationFilter(
    private val accessTokenHolder: AccessTokenService,
    private val jwtDecoder: JwtDecoder,
) : Filter {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    }

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        filterChain: FilterChain
    ) {
        doFilterInternal(request as HttpServletRequest, response as HttpServletResponse, filterChain)
    }

    private fun doFilterInternal(
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
        } catch (ex: TokenExpiredException) {
            LOGGER.warn("Token expired", ex)
            SecurityContextHolder.clearContext()
        } finally {
            filterChain.doFilter(request, response)
        }
    }
}
