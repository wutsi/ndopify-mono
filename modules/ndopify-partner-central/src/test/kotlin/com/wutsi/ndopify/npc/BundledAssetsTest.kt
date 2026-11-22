package com.wutsi.ndopify.npc

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** Checks the classpath bundles built by `build-assets.mjs` during `generate-resources`. */
class BundledAssetsTest {
    @Test
    fun cssBundleIsBuiltInOrder() {
        val css = resource("public/assets/dist/app.min.css")
        assertTrue(css.length > 10_000, "css bundle is ${css.length} bytes")
        assertTrue(css.contains(".site-header")) // partner-central.css, first
        assertTrue(css.contains(".connect-steps")) // telegram.css, last
        assertTrue(css.indexOf(".site-header") < css.indexOf(".connect-steps"))
    }

    @Test
    fun jsBundleHasTheSharedScripts() {
        val js = resource("public/assets/dist/app.min.js")
        assertTrue(js.length > 10_000, "js bundle is ${js.length} bytes")
        assertTrue(js.contains("NdDirectUpload"))
        assertTrue(js.contains("data-page"))
        assertTrue(js.indexOf("NdDirectUpload") < js.indexOf("data-page"))
    }

    @Test
    fun jsBundleRegistersTheServiceWorker() {
        assertTrue(resource("public/assets/dist/app.min.js").contains("serviceWorker"))
    }

    private fun resource(path: String): String =
        javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
            ?: fail("missing classpath resource $path")
}
