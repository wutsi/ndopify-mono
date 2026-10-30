package com.wutsi.ndopify.security.server.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.TokenExpiredException
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.error.server.exception.UnauthorizedException
import com.wutsi.ndopify.security.dto.JwtPrincipal
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * Issues and verifies the access tokens, signed with HS256. The secret (`ndopify.security.jwt.secret`) is a dummy one
 * in local dev and tests, and the `JWT_SECRET` environment variable on deployed servers. Only ndopify-server has it:
 * Partner Central and the e-signature portal read the claims without checking the signature, ndopify-server checks it
 * on each of their calls. A token lives as long as its application's TTL ([AccessTokenProperties]).
 */
@Service
@EnableConfigurationProperties(AccessTokenProperties::class)
open class AccessTokenService(
    private val clock: Clock,
    private val request: HttpServletRequest,
    @Value("\${ndopify.security.jwt.secret}") secret: String,
    private val properties: AccessTokenProperties,
) {
    companion object {
        const val ISSUER = "Ndopify"
        const val MIN_SECRET_BYTES = 32 // HS256's key size
    }

    private val algorithm: Algorithm

    init {
        require(secret.toByteArray().size >= MIN_SECRET_BYTES) {
            "ndopify.security.jwt.secret must have at least $MIN_SECRET_BYTES bytes"
        }
        algorithm = Algorithm.HMAC256(secret)
    }

    fun create(
        application: String,
        userId: Long,
        roles: Array<String>,
        tenantId: Long?,
        ttlSeconds: Int = properties.ttl(application).seconds.toInt(),
        partyId: Long? = null,
    ): String {
        val algo = getAlgorithm()
        val now = clock.millis()
        val builder = JWT.create()
            .withIssuer(ISSUER)
            .withClaim(JwtPrincipal.CLAIM_USER_ID, userId)
            .withClaim(JwtPrincipal.CLAIM_TENANT_ID, tenantId)
            .withClaim(JwtPrincipal.CLAIM_APPLICATION, application)
            .withArrayClaim(JwtPrincipal.CLAIM_ROLE, roles)
            .withSubject(userId.toString())
            .withIssuedAt(Instant.ofEpochMilli(now))
            .withExpiresAt(Instant.ofEpochMilli(now + ttlSeconds * 1000))

        if (tenantId != null) {
            builder.withClaim(JwtPrincipal.CLAIM_TENANT_ID, tenantId)
        }
        // So that a client knows its party without looking up the user. The server itself doesn't rely on it:
        // CallerService still finds the party through the membership.
        if (partyId != null) {
            builder.withClaim(JwtPrincipal.CLAIM_PARTY_ID, partyId)
        }

        return builder.sign(algo)
    }

    @Throws(TokenExpiredException::class)
    fun decode(accessToken: String): JwtPrincipal {
        val verifier = JWT.require(getAlgorithm())
            .withIssuer(Companion.ISSUER)
            .build()
        return JwtPrincipal(verifier.verify(accessToken))
    }

    fun getPrincipal(): JwtPrincipal {
        return getPrincipalOrNull()
            ?: throw UnauthorizedException(
                error = Error(code = ErrorCode.AUTH_UNAUTHORIZED)
            )
    }

    fun getPrincipalOrNull(): JwtPrincipal? {
        val accessToken = request.getHeader("Authorization")
            ?.removePrefix("Bearer ")
            ?: return null

        try {
            return decode(accessToken)
        } catch (ex: Exception) {
            throw ForbiddenException(
                error = Error(code = ErrorCode.AUTH_UNAUTHORIZED, message = ex.message),
                ex = ex,
            )
        }
    }

    private fun getAlgorithm(): Algorithm {
        return algorithm
    }
}
