package com.wutsi.ndopify.npc.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.npc.client.ndopify.NdTenantClient
import com.wutsi.ndopify.refdata.dto.SearchTenantResponse
import com.wutsi.ndopify.refdata.dto.Tenant
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TenantContextTest {
    private val tenants = listOf(
        Tenant(id = 1, partnerCentralUrl = "http://localhost:8081"),
        Tenant(id = 2, partnerCentralUrl = null),
        Tenant(id = 3, partnerCentralUrl = "https://partner.ndopify.cm"),
    )
    private val api = mock<NdTenantClient> {
        on { search() } doReturn SearchTenantResponse(tenants = tenants)
    }

    @Test
    fun `resolves the tenant whose partner-central URL prefixes the request URL`() {
        val context = TenantContext(api, request("http", "localhost", 8081, "/login"))

        assertEquals(1L, context.getTenantId())
    }

    @Test
    fun `skips tenants without a partner-central URL`() {
        val context = TenantContext(api, request("https", "partner.ndopify.cm", 443, "/kyc/niu"))

        assertEquals(3L, context.getTenantId())
    }

    @Test
    fun `fails when no tenant matches the request URL`() {
        val context = TenantContext(api, request("https", "unknown.com", 443, "/login"))

        val ex = assertFailsWith<IllegalStateException> { context.getTenantId() }
        assertEquals("No tenant found for URL: https://unknown.com/login", ex.message)
    }

    @Test
    fun `fails when there is no tenant`() {
        val api = mock<NdTenantClient> {
            on { search() } doReturn SearchTenantResponse()
        }
        val context = TenantContext(api, request("http", "localhost", 8081, "/login"))

        assertFailsWith<IllegalStateException> { context.getTenantId() }
    }

    @Test
    fun `all returns every tenant`() {
        val context = TenantContext(api, request("http", "localhost", 8081, "/"))

        assertEquals(tenants, context.all())
    }

    @Test
    fun `tenants are loaded from the server once`() {
        val context = TenantContext(api, request("http", "localhost", 8081, "/login"))

        context.all()
        context.getTenantId()
        context.getTenantId()

        verify(api, times(1)).search()
    }

    private fun request(scheme: String, host: String, port: Int, uri: String) =
        MockHttpServletRequest("GET", uri).apply {
            this.scheme = scheme
            serverName = host
            serverPort = port
        }
}
