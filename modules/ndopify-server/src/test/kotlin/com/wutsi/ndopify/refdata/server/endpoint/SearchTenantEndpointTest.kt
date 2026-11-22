package com.wutsi.ndopify.refdata.server.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.refdata.dto.SearchTenantResponse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/refdata/SearchTenantEndpoint.sql"])
class SearchTenantEndpointTest : BaseEndpointIntegrationTest() {
    @Test
    fun all() {
        val result = rest.getForEntity("/v1/tenants", SearchTenantResponse::class.java)

        assertEquals(HttpStatus.OK, result.statusCode)

        val tenants = result.body!!.tenants
        assertEquals(5, tenants.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), tenants.map { it.id })
        assertEquals("America/Toronto", tenants[0].timeZone)
        assertEquals("Africa/Douala", tenants[4].timeZone) // Default
    }

    @Test
    fun `pwa icon variants`() {
        val tenants = rest.getForEntity("/v1/tenants", SearchTenantResponse::class.java).body!!.tenants

        val tenant = tenants.first { it.id == 1L }
        assertEquals("https://cdn.ndopify.cm/icon-192.png", tenant.icon192Url)
        assertEquals("https://cdn.ndopify.cm/icon-512.png", tenant.icon512Url)
        assertEquals("https://cdn.ndopify.cm/icon-maskable-512.png", tenant.maskableIconUrl)
        assertEquals("https://cdn.ndopify.cm/apple-touch-icon.png", tenant.appleTouchIconUrl)

        val plain = tenants.first { it.id == 2L }
        assertNull(plain.icon192Url)
        assertNull(plain.icon512Url)
        assertNull(plain.maskableIconUrl)
        assertNull(plain.appleTouchIconUrl)
    }

    @Test
    fun byStatus() {
        val result = rest.getForEntity("/v1/tenants?active=true", SearchTenantResponse::class.java)

        assertEquals(HttpStatus.OK, result.statusCode)

        val tenants = result.body!!.tenants
        assertEquals(3, tenants.size)
        assertEquals(listOf(1L, 2L, 5L), tenants.map { it.id })
    }
}
