package com.wutsi.koki.security.dto

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

open class JWTDecoder {
    companion object {
        const val ISSUER = "Koki"
    }

    fun decode(accessToken: String): JWTPrincipal {
        val verifier = JWT.require(getAlgorithm())
            .withIssuer(ISSUER)
            .build()
        return JWTPrincipal(verifier.verify(accessToken))
    }

    private fun getAlgorithm(): Algorithm {
        return Algorithm.none()
    }
}
