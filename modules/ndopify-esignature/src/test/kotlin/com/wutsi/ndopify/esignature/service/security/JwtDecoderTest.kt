package com.wutsi.ndopify.esignature.service.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
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

    companion object {
        // ndopify-server's algorithm, with any secret: the signature isn't checked here
        private val ALGORITHM = Algorithm.HMAC256("any-secret")
    }

    @Test
    fun `decodes the claims of an e-signature token`() {
        val principal = decoder.decode(token().sign(ALGORITHM))

        assertEquals(11L, principal.getUserId())
        assertEquals(1L, principal.getTenantId())
        assertEquals("11", principal.getSubject())
        assertEquals("11", principal.name)
        assertEquals(ApplicationCode.E_SIGNATURE, principal.getApplication())
        assertContentEquals(arrayOf("agent"), principal.getRoles())
    }

    @Test
    fun `rejects a partner-central token`() {
        val accessToken = token(application = ApplicationCode.PARTNER_CENTRAL).sign(ALGORITHM)

        val ex = assertFailsWith<IncorrectClaimException> { decoder.decode(accessToken) }
        assertEquals(JwtPrincipal.CLAIM_APPLICATION, ex.claimName)
    }

    @Test
    fun `decodes a token without tenant`() {
        val accessToken = JWT.create()
            .withIssuer(JwtDecoder.ISSUER)
            .withSubject("11")
            .withClaim(JwtPrincipal.CLAIM_USER_ID, 11L)
            .withClaim(JwtPrincipal.CLAIM_APPLICATION, ApplicationCode.E_SIGNATURE)
            .sign(ALGORITHM)

        assertEquals(null, decoder.decode(accessToken).getTenantId())
    }

    @Test
    fun `decodes a token without expiry`() {
        val principal = decoder.decode(token(expiresAt = null).sign(ALGORITHM))

        assertEquals(11L, principal.getUserId())
    }

    @Test
    fun `rejects a token of another application`() {
        val accessToken = token(application = ApplicationCode.ADMIN_CONSOLE).sign(ALGORITHM)

        val ex = assertFailsWith<IncorrectClaimException> { decoder.decode(accessToken) }
        assertEquals(JwtPrincipal.CLAIM_APPLICATION, ex.claimName)
    }

    @Test
    fun `rejects a token without application`() {
        val accessToken = token(application = null).sign(ALGORITHM)

        assertFailsWith<IncorrectClaimException> { decoder.decode(accessToken) }
    }

    @Test
    fun `doesn't check the issuer, ndopify-server does`() {
        val accessToken = token(issuer = "Koki").sign(ALGORITHM)

        assertEquals(11L, decoder.decode(accessToken).getUserId())
    }

    @Test
    fun `rejects an expired token`() {
        val accessToken = token(expiresAt = Instant.now().minusSeconds(60)).sign(ALGORITHM)

        assertFailsWith<TokenExpiredException> { decoder.decode(accessToken) }
    }

    @Test
    fun `doesn't check the algorithm, ndopify-server does`() {
        val accessToken = token().sign(Algorithm.none())

        assertEquals(11L, decoder.decode(accessToken).getUserId())
    }

    @Test
    fun `doesn't check the signature, ndopify-server does`() {
        val accessToken = token().sign(Algorithm.HMAC256("another-secret"))

        assertEquals(11L, decoder.decode(accessToken).getUserId())
    }

    @Test
    fun `rejects a malformed token`() {
        assertFailsWith<JWTDecodeException> { decoder.decode("not-a-jwt") }
    }

    // Same claims as ndopify-server's AccessTokenService.
    private fun token(
        issuer: String = JwtDecoder.ISSUER,
        application: String? = ApplicationCode.E_SIGNATURE,
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
