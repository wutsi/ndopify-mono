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
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
open class AccessTokenService(
    private val clock: Clock,
    private val request: HttpServletRequest,
) {
    companion object {
        const val ISSUER = "Ndopify"
    }

    fun create(
        application: String,
        userId: Long,
        roles: Array<String>,
        tenantId: Long?,
        ttlSeconds: Int?,
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

        if (tenantId != null) {
            builder.withClaim(JwtPrincipal.CLAIM_TENANT_ID, tenantId)
        }
        if (ttlSeconds != null) {
            builder.withExpiresAt(Instant.ofEpochMilli(now + ttlSeconds * 1000))
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
        val accessToken = request.getHeader("Authorization")?.removePrefix("Bearer ")
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
        return Algorithm.none()
    }
}
