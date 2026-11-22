package com.wutsi.ndopify.npc.config

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals

class AssetCacheFilterTest {
    private val filter = AssetCacheFilter()

    private fun cacheControl(path: String): String {
        val response = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("GET", path), response, MockFilterChain())
        return response.getHeader("Cache-Control") ?: ""
    }

    @Test
    fun bundleIsImmutable() =
        assertEquals("public, max-age=31536000, immutable", cacheControl("/assets/dist/app.min.css"))

    @Test
    fun otherAssetsAreCachedForADay() =
        assertEquals("public, max-age=86400", cacheControl("/assets/images/logo.png"))

    @Test
    fun pagesAreRevalidated() =
        assertEquals("no-cache, must-revalidate", cacheControl("/"))

    @Test
    fun pwaFilesAreRevalidated() {
        assertEquals("no-cache, must-revalidate", cacheControl("/sw.js"))
        assertEquals("no-cache, must-revalidate", cacheControl("/manifest.json"))
        assertEquals("no-cache, must-revalidate", cacheControl("/offline.html"))
    }
}
