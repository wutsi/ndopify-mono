package com.wutsi.ndopify.web.core.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.refdata.dto.SearchTenantResponse
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.core.client.ndopify.NdTenantClient
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TenantContextTest {
    private val tenants = listOf(
        Tenant(
            id = 1,
            partnerCentralUrl = "http://localhost:8081",
            publicPortalUrl = "http://localhost:8082",
            eSignaturePortalUrl = "http://localhost:8083",
        ),
        Tenant(id = 2),
        Tenant(
            id = 3,
            partnerCentralUrl = "https://partner.ndopify.cm",
            publicPortalUrl = "https://www.ndopify.cm",
            eSignaturePortalUrl = "https://sign.ndopify.cm",
        ),
    )
    private val api = mock<NdTenantClient> {
        on { search() } doReturn SearchTenantResponse(tenants = tenants)
    }

    @Test
    fun `resolves the tenant whose public portal URL prefixes the request URL`() {
        val context = TenantContext(api, request("http", "localhost", 8082, "/"), TenantPortal.PUBLIC_PORTAL)

        assertEquals(1L, context.getTenantId())
    }

    @Test
    fun `resolves the tenant whose partner central URL prefixes the request URL`() {
        val context = TenantContext(api, request("https", "partner.ndopify.cm", 443, "/"), TenantPortal.PARTNER_CENTRAL)

        assertEquals(3L, context.getTenantId())
    }

    @Test
    fun `resolves the tenant whose e-signature portal URL prefixes the request URL`() {
        val context = TenantContext(api, request("http", "localhost", 8083, "/sign/1"), TenantPortal.E_SIGNATURE)

        assertEquals(1L, context.getTenantId())
    }

    @Test
    fun `ignores the URLs of the other portals`() {
        val context = TenantContext(api, request("http", "localhost", 8082, "/"), TenantPortal.PARTNER_CENTRAL)

        assertFailsWith<IllegalStateException> { context.getTenantId() }
    }

    @Test
    fun `skips tenants without a URL for the portal`() {
        val context = TenantContext(api, request("https", "www.ndopify.cm", 443, "/"), TenantPortal.PUBLIC_PORTAL)

        assertEquals(3L, context.getTenantId())
    }

    @Test
    fun `fails when no tenant matches the request URL`() {
        val context = TenantContext(api, request("https", "unknown.com", 443, "/"), TenantPortal.PUBLIC_PORTAL)

        val ex = assertFailsWith<IllegalStateException> { context.getTenantId() }
        assertEquals("No tenant found for URL: https://unknown.com/", ex.message)
    }

    @Test
    fun `fails when there is no tenant`() {
        val api = mock<NdTenantClient> {
            on { search() } doReturn SearchTenantResponse()
        }
        val context = TenantContext(api, request("http", "localhost", 8082, "/"), TenantPortal.PUBLIC_PORTAL)

        assertFailsWith<IllegalStateException> { context.getTenantId() }
    }

    @Test
    fun `all returns every tenant`() {
        val context = TenantContext(api, request("http", "localhost", 8082, "/"), TenantPortal.PUBLIC_PORTAL)

        assertEquals(tenants, context.all())
    }

    @Test
    fun `tenants are loaded from the server once`() {
        val context = TenantContext(api, request("http", "localhost", 8082, "/"), TenantPortal.PUBLIC_PORTAL)

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
