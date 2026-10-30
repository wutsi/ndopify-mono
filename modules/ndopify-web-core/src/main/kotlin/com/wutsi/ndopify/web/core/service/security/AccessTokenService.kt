package com.wutsi.ndopify.web.core.service.security

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import java.time.Duration

/**
 * Keeps the access token in the [cookieName] cookie (`HttpOnly`, `SameSite=Lax`, `Secure` over HTTPS). Not a
 * `@Service`: each web app declares it as a bean with its own cookie name and [ttl].
 *
 * @param ttl the cookie's `Max-Age`, the same as the token's TTL on ndopify-server so that the cookie doesn't outlive
 *   it; `null` for a session cookie.
 */
class AccessTokenService(
    private val request: HttpServletRequest,
    private val response: HttpServletResponse,
    val cookieName: String,
    private val ttl: Duration?,
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
            .apply { ttl?.let { maxAge(it) } }
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

    private fun findCookie(request: HttpServletRequest): Cookie? {
        return request.cookies?.find { cookie -> cookie.name == cookieName }
    }
}
