package com.wutsi.ndopify.npc.service.security

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.IncorrectClaimException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.security.dto.JwtPrincipal
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Reads the claims of an access token **without checking its signature**: only ndopify-server has the secret, and it
 * checks the signature on every call this app makes with the token (401 otherwise, see `HttpClientErrorHandler`). So
 * a claim is never enough on its own: use it to pick what to ask ndopify-server, never to show or allow anything that
 * ndopify-server doesn't check again. Nor are the algorithm or the issuer checked: anyone can write them.
 */
@Service
open class JwtDecoder {
    companion object {
        const val ISSUER = "Ndopify"
    }

    fun decode(accessToken: String): JwtPrincipal {
        val jwt = JWT.decode(accessToken)
        val expiresAt = jwt.expiresAtAsInstant
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw TokenExpiredException("The token has expired on $expiresAt", expiresAt)
        }
        val app = jwt.getClaim(JwtPrincipal.CLAIM_APPLICATION)
        if (app.asString() != ApplicationCode.PARTNER_CENTRAL) {
            throw IncorrectClaimException("Invalid application", JwtPrincipal.CLAIM_APPLICATION, app)
        }
        return JwtPrincipal(jwt)
    }
}
