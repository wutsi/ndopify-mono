package com.wutsi.ndopify.npc.service.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service

@Service
class AccessTokenHolder(private val request: HttpServletRequest, private val response: HttpServletResponse) {
    companion object {
        const val ACCESS_TOKEN_COOKIE = "__npc_access_token"
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
        return request.cookies?.find { cookie -> cookie.name == "access_token" }
    }
}
