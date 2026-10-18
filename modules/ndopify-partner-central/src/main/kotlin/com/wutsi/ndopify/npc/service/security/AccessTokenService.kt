package com.wutsi.ndopify.npc.service.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service

@Service
class AccessTokenService(private val request: HttpServletRequest, private val response: HttpServletResponse) {
    companion object {
        const val ACCESS_TOKEN_COOKIE = "__npc_access_token"

        // Request attribute set by JwtAuthenticationFilter when the cookie's token doesn't decode (ex: expired):
        // the cookie is still in the request, but mustn't be sent to ndopify-server, which would answer 401.
        const val ACCESS_TOKEN_REJECTED = "com.wutsi.ndopify.npc.access-token-rejected"
    }

    fun set(accessToken: String) {
        val cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, accessToken)
            .path("/")
            .httpOnly(true)
            .secure(request.isSecure)
            .sameSite("Lax")
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
