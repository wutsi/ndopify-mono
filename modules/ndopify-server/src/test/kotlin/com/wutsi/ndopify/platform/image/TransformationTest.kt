package com.wutsi.ndopify.platform.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransformationTest {
    @Test
    fun `quality 1 and 100 are accepted`() {
        assertEquals(1, Transformation(quality = 1).quality)
        assertEquals(100, Transformation(quality = 100).quality)
    }

    @Test
    fun `a quality below 1 is rejected`() {
        assertFailsWith<IllegalArgumentException> { Transformation(quality = 0) }
    }

    @Test
    fun `a quality above 100 is rejected`() {
        assertFailsWith<IllegalArgumentException> { Transformation(quality = 101) }
    }
}
