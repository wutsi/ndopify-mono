package com.wutsi.ndopify.util.jpa

import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonHelperTest {
    private val helper = JsonHelper(JsonMapper())

    data class Sample(val name: String? = null, val count: Int = 0)

    @Test
    fun `write then read round-trips`() {
        val json = helper.write(Sample(name = "a", count = 3))

        assertEquals(Sample(name = "a", count = 3), helper.read(json, Sample::class.java))
    }

    @Test
    fun `read - a blank payload is null`() {
        assertNull(helper.read(null, Sample::class.java))
        assertNull(helper.read("  ", Sample::class.java))
    }

    @Test
    fun `write - null is null`() {
        assertNull(helper.write(null))
    }
}
