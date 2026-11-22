package com.wutsi.ndopify.web

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** Checks the one CSS and one JS bundle built from `src/main/assets/` by `build-assets.mjs`. */
class BundledAssetsTest {
    @Test
    fun cssBundleIsBuiltInOrder() {
        val css = resource("public/assets/dist/app.min.css")
        assertTrue(css.length > 10_000, "css bundle is ${css.length} bytes")
        assertTrue(css.contains(".site-header")) // styles.css, first
        assertTrue(css.contains(".audit-note")) // listing.css
        assertTrue(css.contains(".agent-card")) // proposal.css
        assertTrue(css.contains(".visit-address")) // visit.css, last
        assertTrue(css.indexOf(".site-header") < css.indexOf(".audit-note"))
        assertTrue(css.indexOf(".audit-note") < css.indexOf(".agent-card"))
        assertTrue(css.indexOf(".agent-card") < css.indexOf(".visit-address"))
    }

    @Test
    fun jsBundleHasTheScriptsInOrder() {
        val js = resource("public/assets/dist/app.min.js")
        assertTrue(js.length > 10_000, "js bundle is ${js.length} bytes")
        assertTrue(js.contains("data-amount")) // main.js, first
        assertTrue(js.contains("data-lightbox")) // listing.js
        assertTrue(js.contains("data-visit-request-form")) // proposal.js
        assertTrue(js.contains("#cancel")) // visit.js, last
        assertTrue(js.indexOf("data-amount") < js.indexOf("data-lightbox"))
        assertTrue(js.indexOf("data-lightbox") < js.indexOf("data-visit-request-form"))
        assertTrue(js.indexOf("data-visit-request-form") < js.indexOf("#cancel"))
    }

    private fun resource(path: String): String =
        javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
            ?: fail("missing classpath resource $path")
}
