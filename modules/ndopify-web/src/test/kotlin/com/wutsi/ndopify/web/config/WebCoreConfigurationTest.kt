package com.wutsi.ndopify.web.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.IncorrectClaimException
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.web.core.service.security.JwtDecoder
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Clock
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WebCoreConfigurationTest {
    private val config = WebCoreConfiguration(Clock.systemUTC())

    @Test
    fun `reads the access token from the web cookie`() {
        val request = MockHttpServletRequest().apply { setCookies(Cookie("__nweb_access_token", "abc")) }

        val service = config.accessTokenService(request, MockHttpServletResponse())

        assertEquals("__nweb_access_token", service.cookieName)
        assertEquals("abc", service.get())
    }

    @Test
    fun `the cookie lives as long as the token`() {
        val response = MockHttpServletResponse()
        val service = config.accessTokenService(MockHttpServletRequest(), response)

        service.set(token(ApplicationCode.PUBLIC_PORTAL))

        val cookie = response.getHeader("Set-Cookie")!!
        assertTrue(cookie.startsWith("__nweb_access_token="), cookie)
        assertTrue(Regex("Max-Age=([1-9]\\d*)").containsMatchIn(cookie), cookie)
    }

    @Test
    fun `accepts a public-portal token`() {
        val principal = config.jwtDecoder().decode(token(ApplicationCode.PUBLIC_PORTAL))

        assertEquals(ApplicationCode.PUBLIC_PORTAL, principal.getApplication())
    }

    @Test
    fun `rejects the token of another application`() {
        val decoder: JwtDecoder = config.jwtDecoder()

        assertFailsWith<IncorrectClaimException> { decoder.decode(token(ApplicationCode.E_SIGNATURE)) }
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
