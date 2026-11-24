package com.wutsi.ndopify.web.core.service.security

import com.auth0.jwt.JWT
import com.wutsi.ndopify.web.core.client.ndopify.NdTokenClient
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock
import java.time.Duration

/**
 * Renews the access-token cookie's token once a day, through ndopify-server's `POST /v1/auth/refresh`.
 *
 * It renews on the token's **age**, not only near its expiry: ndopify-server re-issues it for a fresh lifetime, so a
 * visit on day 20 of a 30-day life must extend it, otherwise an agent active every 20 days would still be cut off on
 * day 30. The current request keeps the old token (valid for at least the interval) and later requests use the new
 * cookie.
 *
 * Silent: a token it can't decode (expired, another application's) is left to [JwtAuthenticationFilter], and any
 * failure of the call (network, 409 past the session's cap, 5xx) is logged at debug and ignored, so the page renders
 * with the current token, which then expires normally. Static assets never trigger a renewal.
 */
class AccessTokenRenewalFilter(
    private val accessTokenHolder: AccessTokenService,
    private val jwtDecoder: JwtDecoder,
    private val tokenClient: NdTokenClient,
    private val clock: Clock,
    private val renewalInterval: Duration = Duration.ofDays(1),
) : OncePerRequestFilter() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AccessTokenRenewalFilter::class.java)
        private val SKIPPED_PATHS = setOf("/sw.js", "/offline.html", "/manifest.json", "/favicon.ico", "/error")
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.servletPath.startsWith("/assets/") || request.servletPath in SKIPPED_PATHS

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        try {
            renew()
        } catch (ex: Exception) {
            LOGGER.debug("Access token not renewed", ex)
        }
        chain.doFilter(request, response)
    }

    private fun renew() {
        val accessToken = accessTokenHolder.get() ?: return
        jwtDecoder.decode(accessToken) // expired, foreign or malformed: left to JwtAuthenticationFilter
        val jwt = JWT.decode(accessToken)
        val issuedAt = jwt.issuedAtAsInstant ?: return
        val expiresAt = jwt.expiresAtAsInstant ?: return
        val now = clock.instant()
        val aged = Duration.between(issuedAt, now) >= renewalInterval
        val expiring = Duration.between(now, expiresAt) < renewalInterval
        if (!aged && !expiring) {
            return
        }
        val newToken = tokenClient.refresh(accessToken)
        if (newToken.isNotBlank()) {
            accessTokenHolder.set(newToken)
        }
    }
}
