package com.wutsi.ndopify.web.core.service.security

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTDecodeException
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import java.time.Clock
import java.time.Duration

/**
 * Keeps the access token in the [cookieName] cookie (`HttpOnly`, `SameSite=Lax`, `Secure` over HTTPS). Not a
 * `@Service`: each web app declares it as a bean with its own cookie name.
 *
 * The cookie lives as long as the token: its `Max-Age` is the time left until the token's `exp`, read without checking
 * the signature (it only tells the browser when to drop the cookie; ndopify-server checks the token on every call). So
 * the TTL is only set on ndopify-server, per application. A token without `exp`, or that doesn't decode, gets a session
 * cookie, as does any token when [persistent] is false (signed out when the browser closes, ex: for a shared computer).
 */
class AccessTokenService(
    private val request: HttpServletRequest,
    private val response: HttpServletResponse,
    val cookieName: String,
    private val persistent: Boolean = true,
    private val clock: Clock = Clock.systemUTC(),
) {
    companion object {
        // Request attribute set by JwtAuthenticationFilter when the cookie's token doesn't decode (ex: expired):
        // the cookie is still in the request, but mustn't be sent to ndopify-server, which would answer 401.
        const val ACCESS_TOKEN_REJECTED = "com.wutsi.ndopify.web.core.access-token-rejected"
    }

    fun set(accessToken: String) {
        val cookie = ResponseCookie.from(cookieName, accessToken)
            .path("/")
            .httpOnly(true)
            .secure(request.isSecure)
            .sameSite("Lax")
            .apply { maxAge(accessToken)?.let { maxAge(it) } }
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    fun remove() {
        val cookie = ResponseCookie.from(cookieName, "")
            .path("/")
            .httpOnly(true)
            .secure(request.isSecure)
            .sameSite("Lax")
            .maxAge(0)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    fun get(): String? {
        val cookie = findCookie(request)
        return cookie?.value
    }

    private fun maxAge(accessToken: String): Duration? {
        if (!persistent) {
            return null
        }
        val expiresAt = try {
            JWT.decode(accessToken).expiresAtAsInstant
        } catch (ex: JWTDecodeException) {
            null
        } ?: return null
        val left = Duration.between(clock.instant(), expiresAt)
        return if (left.isNegative) Duration.ZERO else left
    }

    private fun findCookie(request: HttpServletRequest): Cookie? {
        return request.cookies?.find { cookie -> cookie.name == cookieName }
    }
}
