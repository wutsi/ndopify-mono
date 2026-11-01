package com.wutsi.ndopify.web.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.web.core.service.security.JwtAuthentication
import com.wutsi.ndopify.web.core.service.security.JwtDecoder
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Clock
import java.time.Instant
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityConfigurationTest {
    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()
    private val chain = MockFilterChain()

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `a public-portal token signs the request in`() {
        run(token(ApplicationCode.PUBLIC_PORTAL))

        assertTrue(SecurityContextHolder.getContext().authentication is JwtAuthentication)
        assertNotNull(chain.request)
        assertNull(response.getHeader("Set-Cookie"))
    }

    @Test
    fun `no cookie leaves the request signed out`() {
        run(null)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertNotNull(chain.request)
        assertNull(response.getHeader("Set-Cookie"))
    }

    @Test
    fun `a token of another application is signed out and its cookie cleared`() {
        run(token(ApplicationCode.E_SIGNATURE))

        assertNull(SecurityContextHolder.getContext().authentication)
        assertNotNull(chain.request)
        assertCleared()
    }

    @Test
    fun `a malformed token is signed out and its cookie cleared`() {
        run("not-a-jwt")

        assertNull(SecurityContextHolder.getContext().authentication)
        assertNotNull(chain.request)
        assertCleared()
    }

    private fun assertCleared() {
        val cookie = response.getHeader("Set-Cookie")
        assertNotNull(cookie)
        assertTrue(cookie.startsWith("__nweb_access_token=;"), cookie)
        assertTrue(cookie.contains("Max-Age=0"), cookie)
    }

    private fun run(accessToken: String?) {
        if (accessToken != null) {
            request.setCookies(Cookie("__nweb_access_token", accessToken))
        }
        val core = WebCoreConfiguration(Clock.systemUTC())
        val security = SecurityConfiguration(core.accessTokenService(request, response), core.jwtDecoder())
        security.authorizationFilter().doFilter(request, response, chain)
    }

    private fun token(application: String): String =
        JWT.create()
            .withIssuer(JwtDecoder.ISSUER)
            .withSubject("11")
            .withClaim(JwtPrincipal.CLAIM_USER_ID, 11L)
            .withClaim(JwtPrincipal.CLAIM_TENANT_ID, 1L)
            .withClaim(JwtPrincipal.CLAIM_APPLICATION, application)
            .withArrayClaim(JwtPrincipal.CLAIM_ROLE, emptyArray<String>())
            .withExpiresAt(Instant.now().plusSeconds(3600))
            .sign(Algorithm.HMAC256("any-secret"))
}
