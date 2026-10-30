package com.wutsi.ndopify.web.ui.layout

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.core.service.TenantContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TenantAdviceTest {
    private val tenant = Tenant(id = 1, publicPortalUrl = "http://localhost:8082")

    @Test
    fun `current tenant`() {
        val context = mock<TenantContext> { on { getTenant() } doReturn tenant }

        assertEquals(tenant, TenantAdvice(context).tenant())
    }

    @Test
    fun `fails when the tenant is unknown`() {
        val context = mock<TenantContext> {
            on { getTenant() } doThrow IllegalStateException("No tenant found for URL: http://unknown.com/")
        }

        val ex = assertFailsWith<IllegalStateException> { TenantAdvice(context).tenant() }
        assertEquals("No tenant found for URL: http://unknown.com/", ex.message)
    }
}
