package com.wutsi.ndopify.npc.service.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.ndopify.security.dto.JwtPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwtAuthenticationFilterTest {
    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()
    private val chain = MockFilterChain()

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `authenticates the request from the access token`() {
        filter(token(userId = 11, tenantId = 1)).doFilter(request, response, chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertTrue(auth is JwtAuthentication)
        assertTrue(auth.isAuthenticated)
        assertEquals("11", auth.name)

        val principal = auth.principal as JwtPrincipal
        assertEquals(11L, principal.getUserId())
        assertEquals(1L, principal.getTenantId())
        assertEquals("partner-central", principal.getApplication())

        assertChainCalled()
    }

    @Test
    fun `clears the security context when there is no access token`() {
        SecurityContextHolder.setContext(SecurityContextImpl(TestingAuthenticationToken("stale", "")))

        filter(null).doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertChainCalled()
    }

    @Test
    fun `clears the security context when the access token has expired`() {
        SecurityContextHolder.setContext(SecurityContextImpl(TestingAuthenticationToken("stale", "")))
        val expired = token(userId = 11, tenantId = 1, expiresAt = Instant.now().minusSeconds(60))

        filter(expired).doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertChainCalled()
    }

    @Test
    fun `accepts a token without expiry`() {
        filter(token(userId = 11, tenantId = 1, expiresAt = null)).doFilter(request, response, chain)

        assertTrue(SecurityContextHolder.getContext().authentication is JwtAuthentication)
        assertChainCalled()
    }

    private fun filter(accessToken: String?): JwtAuthenticationFilter {
        val accessTokenService = mock<AccessTokenService> {
            on { get() } doReturn accessToken
        }
        return JwtAuthenticationFilter(accessTokenService, JwtDecoder())
    }

    // Same claims as ndopify-server's AccessTokenService.
    private fun token(userId: Long, tenantId: Long, expiresAt: Instant? = Instant.now().plusSeconds(3600)): String =
        JWT.create()
            .withIssuer(JwtDecoder.ISSUER)
            .withSubject(userId.toString())
            .withClaim(JwtPrincipal.CLAIM_USER_ID, userId)
            .withClaim(JwtPrincipal.CLAIM_TENANT_ID, tenantId)
            .withClaim(JwtPrincipal.CLAIM_APPLICATION, "partner-central")
            .withArrayClaim(JwtPrincipal.CLAIM_ROLE, emptyArray<String>())
            .apply { expiresAt?.let { withExpiresAt(it) } }
            .sign(Algorithm.none())

    private fun assertChainCalled() {
        assertNotNull(chain.request, "the filter chain must continue")
    }
}
