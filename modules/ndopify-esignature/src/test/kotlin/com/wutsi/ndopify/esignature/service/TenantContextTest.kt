package com.wutsi.ndopify.esignature.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.esignature.client.ndopify.NdTenantClient
import com.wutsi.ndopify.refdata.dto.SearchTenantResponse
import com.wutsi.ndopify.refdata.dto.Tenant
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TenantContextTest {
    private val tenants = listOf(
        Tenant(id = 1, eSignaturePortalUrl = "http://localhost:8081"),
        Tenant(id = 2, eSignaturePortalUrl = null),
        Tenant(id = 3, eSignaturePortalUrl = "https://sign.ndopify.cm"),
    )
    private val api = mock<NdTenantClient> {
        on { search() } doReturn SearchTenantResponse(tenants = tenants)
    }

    @Test
    fun `resolves the tenant whose e-signature portal URL prefixes the request URL`() {
        val context = TenantContext(api, request("http", "localhost", 8081, "/sign/abc"))

        assertEquals(1L, context.getTenantId())
    }

    @Test
    fun `skips tenants without a e-signature portal URL`() {
        val context = TenantContext(api, request("https", "sign.ndopify.cm", 443, "/sign/abc"))

        assertEquals(3L, context.getTenantId())
    }

    @Test
    fun `fails when no tenant matches the request URL`() {
        val context = TenantContext(api, request("https", "unknown.com", 443, "/sign/abc"))

        val ex = assertFailsWith<IllegalStateException> { context.getTenantId() }
        assertEquals("No tenant found for URL: https://unknown.com/sign/abc", ex.message)
    }

    @Test
    fun `fails when there is no tenant`() {
        val api = mock<NdTenantClient> {
            on { search() } doReturn SearchTenantResponse()
        }
        val context = TenantContext(api, request("http", "localhost", 8081, "/sign/abc"))

        assertFailsWith<IllegalStateException> { context.getTenantId() }
    }

    @Test
    fun `all returns every tenant`() {
        val context = TenantContext(api, request("http", "localhost", 8081, "/"))

        assertEquals(tenants, context.all())
    }

    @Test
    fun `tenants are loaded from the server once`() {
        val context = TenantContext(api, request("http", "localhost", 8081, "/sign/abc"))

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
