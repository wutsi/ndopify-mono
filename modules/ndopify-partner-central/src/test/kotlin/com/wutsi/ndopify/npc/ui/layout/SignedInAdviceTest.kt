package com.wutsi.ndopify.npc.ui.layout

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.wutsi.ndopify.npc.service.security.JwtAuthentication
import com.wutsi.ndopify.npc.service.security.JwtDecoder
import com.wutsi.ndopify.security.dto.JwtPrincipal
import org.junit.jupiter.api.AfterEach
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignedInAdviceTest {
    private val advice = SignedInAdvice()

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `signed in`() {
        signIn()

        assertTrue(advice.signedIn())
    }

    @Test
    fun `no authentication`() {
        assertFalse(advice.signedIn())
    }

    @Test
    fun anonymous() {
        SecurityContextHolder.setContext(
            SecurityContextImpl(
                AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")),
            ),
        )

        assertFalse(advice.signedIn())
    }

    @Test
    fun `not authenticated`() {
        signIn().isAuthenticated = false

        assertFalse(advice.signedIn())
    }

    companion object {
        fun signIn(userId: Long = 11L): JwtAuthentication {
            val token = JWT.create().withIssuer(JwtDecoder.ISSUER).withClaim(JwtPrincipal.CLAIM_USER_ID, userId)
                .sign(Algorithm.HMAC256("any-secret")) // ndopify-server signs with HS256
            val authentication = JwtAuthentication(JwtPrincipal(JWT.decode(token)))
            SecurityContextHolder.setContext(SecurityContextImpl(authentication))
            return authentication
        }
    }
}
