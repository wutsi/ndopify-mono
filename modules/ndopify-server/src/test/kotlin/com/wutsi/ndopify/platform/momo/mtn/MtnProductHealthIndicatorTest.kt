package com.wutsi.ndopify.platform.momo.mtn

import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.springframework.boot.health.contributor.Status
import kotlin.test.Test
import kotlin.test.assertEquals

class MtnProductHealthIndicatorTest {
    private val product = mock<MtnProduct>()
    private val hc = MtnProductHealthIndicator("sandbox", product)

    @Test
    fun up() {
        val result = hc.health()

        assertEquals(Status.UP, result.status)
    }

    @Test
    fun down() {
        doThrow(RuntimeException::class).whenever(product).authenticate()
        val result = hc.health()

        assertEquals(Status.DOWN, result.status)
    }
}
