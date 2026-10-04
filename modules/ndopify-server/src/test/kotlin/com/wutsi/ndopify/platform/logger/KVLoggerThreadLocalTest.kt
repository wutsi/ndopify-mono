package com.wutsi.koki.platform.logger

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class KVLoggerThreadLocalTest {
    @BeforeEach
    fun setUp() {
        KVLoggerThreadLocal.remove()
    }

    @Test
    fun test() {
        assertNull(KVLoggerThreadLocal.get())

        val value = DefaultKVLogger()
        KVLoggerThreadLocal.set(value)
        assertEquals(value, KVLoggerThreadLocal.get())

        KVLoggerThreadLocal.remove()
        assertNull(KVLoggerThreadLocal.get())
    }
}
