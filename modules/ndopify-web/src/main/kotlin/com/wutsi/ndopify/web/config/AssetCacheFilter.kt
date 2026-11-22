package com.wutsi.ndopify.web.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Sets the `Cache-Control` header before the chain runs, so the resource handler keeps it:
 * the versioned bundle is immutable for a year, the other assets for a day, and everything
 * else (pages, `/sw.js`, `/offline.html`, `/manifest.json`) is revalidated.
 */
class AssetCacheFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        response.setHeader("Cache-Control", cacheControl(request.requestURI))
        filterChain.doFilter(request, response)
    }

    internal fun cacheControl(path: String): String = when {
        path.startsWith("/assets/dist/") -> "public, max-age=31536000, immutable"
        path.startsWith("/assets/") -> "public, max-age=86400"
        else -> "no-cache, must-revalidate"
    }
}
