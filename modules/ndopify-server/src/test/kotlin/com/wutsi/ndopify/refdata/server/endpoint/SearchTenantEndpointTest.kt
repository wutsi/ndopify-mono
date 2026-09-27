package com.wutsi.ndopify.refdata.server.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.refdata.dto.SearchTenantResponse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/refdata/SearchTenantEndpoint.sql"])
class SearchTenantEndpointTest : BaseEndpointIntegrationTest() {
    @Test
    fun all() {
        val result = rest.getForEntity("/v1/tenants", SearchTenantResponse::class.java)

        assertEquals(HttpStatus.OK, result.statusCode)

        val tenants = result.body!!.tenants
        assertEquals(5, tenants.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), tenants.map { it.id })
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
