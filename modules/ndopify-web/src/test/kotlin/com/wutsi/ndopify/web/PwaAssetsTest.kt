package com.wutsi.ndopify.web

import kotlin.test.Test
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

    private fun resource(path: String): String =
        javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
            ?: fail("missing classpath resource $path")

    private fun resourceBytes(path: String): ByteArray =
        javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
            ?: fail("missing classpath resource $path")
}
