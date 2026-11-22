package com.wutsi.ndopify.web.config

import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class AssetCacheFilterTest {
    private val filter = AssetCacheFilter()

    private fun cacheControl(path: String): String? {
        val response = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("GET", path), response, MockFilterChain())
        return response.getHeader("Cache-Control")
    }

    @Test
    fun `a bundle is immutable`() =
        assertEquals("public, max-age=31536000, immutable", cacheControl("/assets/dist/app-abc123.min.css"))

    @Test
    fun `other assets are cached for a day`() =
        assertEquals("public, max-age=86400", cacheControl("/assets/images/logo.png"))

    @Test
    fun `pages are revalidated`() =
        assertEquals("no-cache, must-revalidate", cacheControl("/match/42"))

    @Test
    fun `pwa files are revalidated`() {
        assertEquals("no-cache, must-revalidate", cacheControl("/sw.js"))
        assertEquals("no-cache, must-revalidate", cacheControl("/offline.html"))
        assertEquals("no-cache, must-revalidate", cacheControl("/manifest.json"))
    }
}
