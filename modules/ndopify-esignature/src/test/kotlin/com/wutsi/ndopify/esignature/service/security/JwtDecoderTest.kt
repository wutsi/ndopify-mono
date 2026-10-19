package com.wutsi.ndopify.esignature.service.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.AlgorithmMismatchException
import com.auth0.jwt.exceptions.IncorrectClaimException
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.security.dto.JwtPrincipal
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwtDecoderTest {
    private val decoder = JwtDecoder()

    @Test
    fun `decodes the claims of a partner-central token`() {
        val principal = decoder.decode(token().sign(Algorithm.none()))

        assertEquals(11L, principal.getUserId())
        assertEquals(1L, principal.getTenantId())
        assertEquals("11", principal.getSubject())
        assertEquals("11", principal.name)
        assertEquals(ApplicationCode.PARTNER_CENTRAL, principal.getApplication())
        assertContentEquals(arrayOf("agent"), principal.getRoles())
    }

    @Test
    fun `decodes a token without tenant`() {
        val accessToken = JWT.create()
            .withIssuer(JwtDecoder.ISSUER)
            .withSubject("11")
            .withClaim(JwtPrincipal.CLAIM_USER_ID, 11L)
            .withClaim(JwtPrincipal.CLAIM_APPLICATION, ApplicationCode.PARTNER_CENTRAL)
            .sign(Algorithm.none())

        assertEquals(null, decoder.decode(accessToken).getTenantId())
    }

    @Test
    fun `decodes a token without expiry`() {
        val principal = decoder.decode(token(expiresAt = null).sign(Algorithm.none()))

        assertEquals(11L, principal.getUserId())
    }

    @Test
    fun `rejects a token of another application`() {
        val accessToken = token(application = ApplicationCode.ADMIN_CONSOLE).sign(Algorithm.none())

        val ex = assertFailsWith<IncorrectClaimException> { decoder.decode(accessToken) }
        assertEquals(JwtPrincipal.CLAIM_APPLICATION, ex.claimName)
    }

    @Test
    fun `rejects a token without application`() {
        val accessToken = token(application = null).sign(Algorithm.none())

        assertFailsWith<IncorrectClaimException> { decoder.decode(accessToken) }
    }

    @Test
    fun `rejects a token of another issuer`() {
        val accessToken = token(issuer = "Koki").sign(Algorithm.none())

        assertFailsWith<IncorrectClaimException> { decoder.decode(accessToken) }
    }

    @Test
    fun `rejects an expired token`() {
        val accessToken = token(expiresAt = Instant.now().minusSeconds(60)).sign(Algorithm.none())

        assertFailsWith<TokenExpiredException> { decoder.decode(accessToken) }
    }

    @Test
    fun `rejects a token signed with another algorithm`() {
        val accessToken = token().sign(Algorithm.HMAC256("secret"))

        assertFailsWith<AlgorithmMismatchException> { decoder.decode(accessToken) }
    }

    @Test
    fun `rejects a malformed token`() {
        assertFailsWith<JWTDecodeException> { decoder.decode("not-a-jwt") }
    }

    // Same claims as ndopify-server's AccessTokenService.
    private fun token(
        issuer: String = JwtDecoder.ISSUER,
        application: String? = ApplicationCode.PARTNER_CENTRAL,
        expiresAt: Instant? = Instant.now().plusSeconds(3600),
    ): JWTCreator.Builder =
        JWT.create()
            .withIssuer(issuer)
            .withSubject("11")
            .withClaim(JwtPrincipal.CLAIM_USER_ID, 11L)
            .withClaim(JwtPrincipal.CLAIM_TENANT_ID, 1L)
            .withArrayClaim(JwtPrincipal.CLAIM_ROLE, arrayOf("agent"))
            .apply { application?.let { withClaim(JwtPrincipal.CLAIM_APPLICATION, it) } }
            .apply { expiresAt?.let { withExpiresAt(it) } }
}
