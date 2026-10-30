package com.wutsi.ndopify.web.core.service.security

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.IncorrectClaimException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.wutsi.ndopify.security.dto.JwtPrincipal
import java.time.Instant

/**
 * Reads the claims of an access token **without checking its signature**: only ndopify-server has the secret, and it
 * checks the signature on every call the web app makes with the token (401 otherwise, see `HttpClientErrorHandler`).
 * So a claim is never enough on its own: use it to pick what to ask ndopify-server, never to show or allow anything
 * that ndopify-server doesn't check again. Nor are the algorithm or the issuer checked: anyone can write them.
 *
 * Not a `@Service`: each web app declares it as a bean with its own [application] (`ApplicationCode`), the only one
 * whose tokens it accepts.
 */
open class JwtDecoder(private val application: String) {
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
        if (app.asString() != application) {
            throw IncorrectClaimException("Invalid application", JwtPrincipal.CLAIM_APPLICATION, app)
        }
        return JwtPrincipal(jwt)
    }
}
