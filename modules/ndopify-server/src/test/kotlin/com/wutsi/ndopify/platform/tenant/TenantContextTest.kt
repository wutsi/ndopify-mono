package com.wutsi.ndopify.platform.tenant

import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TenantContextTest {
    @BeforeEach
    fun setUp() {
        TenantContext.remove()
    }

    @Test
    fun test() {
        assertNull(TenantContext.get())

        TenantContext.set(100L)
        assertEquals(100L, TenantContext.get())

        TenantContext.remove()
        assertNull(TenantContext.get())
    }
}
