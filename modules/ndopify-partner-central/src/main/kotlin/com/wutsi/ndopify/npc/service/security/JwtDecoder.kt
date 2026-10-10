package com.wutsi.ndopify.npc.service.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
        return JwtPrincipal(verifier.verify(accessToken))
    }

    private fun getAlgorithm(): Algorithm {
        return Algorithm.none()
    }
}
