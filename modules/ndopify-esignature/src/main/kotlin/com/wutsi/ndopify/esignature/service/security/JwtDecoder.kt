package com.wutsi.ndopify.esignature.service.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.IncorrectClaimException
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.security.dto.JwtPrincipal
import org.springframework.stereotype.Service

@Service
open class JwtDecoder {
    companion object {
        const val ISSUER = "Ndopify"
    }

    fun decode(accessToken: String): JwtPrincipal {
        val verifier = JWT.require(getAlgorithm())
            .withIssuer(ISSUER)
            .build()
        val jwt = verifier.verify(accessToken)
        val app = jwt.getClaim(JwtPrincipal.CLAIM_APPLICATION)
        if (app.asString() != ApplicationCode.PARTNER_CENTRAL) {
            throw IncorrectClaimException("Invalid application", JwtPrincipal.CLAIM_APPLICATION, app)
        }
        return JwtPrincipal(jwt)
    }

    private fun getAlgorithm(): Algorithm {
        return Algorithm.none()
    }
}
