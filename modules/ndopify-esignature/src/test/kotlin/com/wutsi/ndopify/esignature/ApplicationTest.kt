package com.wutsi.ndopify.esignature

import kotlin.test.Test
import kotlin.test.assertNotNull

/** Only covers the `@SpringBootApplication` class, which the coverage gate counts (its `main` is excluded). */
class ApplicationTest {
    @Test
    fun create() {
        assertNotNull(Application())
    }
}
