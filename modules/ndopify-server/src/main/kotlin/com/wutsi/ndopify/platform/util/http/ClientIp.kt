package com.wutsi.ndopify.platform.util.http

import jakarta.servlet.http.HttpServletRequest

/**
 * The caller's IP, used as the cost guard's per-IP bucket key only. Heroku terminates TLS at its router, so
 * `remoteAddr` alone is the router's address; the first `X-Forwarded-For` entry is used when present, with
 * `remoteAddr` as the fallback. It is never an authorization input.
 */
object ClientIp {
    fun of(request: HttpServletRequest): String? =
        request.getHeader("X-Forwarded-For")
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: request.remoteAddr?.takeIf { it.isNotBlank() }
}
