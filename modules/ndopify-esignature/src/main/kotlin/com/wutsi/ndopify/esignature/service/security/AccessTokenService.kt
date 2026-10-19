package com.wutsi.ndopify.esignature.service.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class AccessTokenService(private val request: HttpServletRequest, private val response: HttpServletResponse) {
    companion object {
        // Same as the token's TTL (ndopify-server), so that the cookie doesn't outlive it.
        private val ACCESS_TOKEN_TTL = Duration.ofHours(1)

        const val ACCESS_TOKEN_COOKIE = "__nes_access_token"

        // Request attribute set by JwtAuthenticationFilter when the cookie's token doesn't decode (ex: expired):
        // the cookie is still in the request, but mustn't be sent to ndopify-server, which would answer 401.
        const val ACCESS_TOKEN_REJECTED = "com.wutsi.ndopify.esignature.access-token-rejected"
    }

    fun set(accessToken: String) {
        val cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, accessToken)
            .path("/")
            .httpOnly(true)
            .secure(request.isSecure)
            .sameSite("Lax")
            .maxAge(ACCESS_TOKEN_TTL)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    fun remove() {
        val cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
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

    private fun findCookie(request: HttpServletRequest): jakarta.servlet.http.Cookie? {
        return request.cookies?.find { cookie -> cookie.name == ACCESS_TOKEN_COOKIE }
    }
}
