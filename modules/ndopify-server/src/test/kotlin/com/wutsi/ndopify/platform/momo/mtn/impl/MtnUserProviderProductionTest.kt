package com.wutsi.ndopify.platform.momo.mtn.impl

import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class MtnUserProviderProductionTest {
    private val provider = MtnUserProviderProduction("A", "B")

    @Test
    fun get() {
        val user = provider.get()
        assertEquals("A", user.id)
        assertEquals("B", user.apiKey)
    }
}
