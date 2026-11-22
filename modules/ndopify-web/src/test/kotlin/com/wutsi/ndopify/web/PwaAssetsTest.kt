package com.wutsi.ndopify.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** Checks the PWA files shipped in `public/` (the worker and the offline page are added by later tasks). */
class PwaAssetsTest {
    @Test
    fun `Ndopify's icon set is shipped`() {
        listOf("icon-192.png", "icon-512.png", "icon-maskable-512.png", "apple-touch-icon.png").forEach {
            assertTrue(resourceBytes("public/assets/images/$it").size > 1_000, it)
        }
    }

    @Test
    fun offlinePageIsSelfContained() {
        val html = resource("public/offline.html")
        assertTrue(html.contains("Vous êtes hors ligne"))
        assertTrue(html.contains("data-retry"))
        assertTrue(html.contains("data-offline-status"))
        assertTrue(html.contains("/assets/images/logo.png"))
        assertTrue(html.contains("Connexion rétablie. Rechargement…"))
        assertFalse(html.contains("fonts.googleapis.com"), "no webfont offline")
        assertFalse(html.contains("/assets/css/"), "no bundle dependency")
        assertFalse(html.contains("<script src="), "scripts must be inline")
        assertFalse(html.contains("rel=\"stylesheet\""), "styles must be inline")
        assertFalse(html.contains("status=online"), "the prototype's review-only branch is dropped")
    }

    private fun resource(path: String): String =
        javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
            ?: fail("missing classpath resource $path")

    private fun resourceBytes(path: String): ByteArray =
        javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
            ?: fail("missing classpath resource $path")
}
