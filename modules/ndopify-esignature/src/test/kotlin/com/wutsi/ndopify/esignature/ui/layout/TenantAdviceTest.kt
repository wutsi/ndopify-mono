package com.wutsi.ndopify.esignature.ui.layout

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.ndopify.esignature.service.TenantContext
import com.wutsi.ndopify.esignature.service.TenantFixtures
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class TenantAdviceTest {
    @Test
    fun `current tenant`() {
        val context = mock<TenantContext> { on { getTenant() } doReturn TenantFixtures.TENANT }

        assertEquals(TenantFixtures.TENANT, TenantAdvice(context).tenant())
    }

    @Test
    fun `null when the tenant is unknown`() {
        val context = mock<TenantContext> {
            on { getTenant() } doThrow IllegalStateException("No tenant found for URL: http://localhost/sign/cp-1")
        }

        assertThrows<IllegalStateException> {
            TenantAdvice(context).tenant()
        }
    }
}
