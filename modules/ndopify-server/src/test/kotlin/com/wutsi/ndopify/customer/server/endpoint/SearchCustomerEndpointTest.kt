package com.wutsi.ndopify.customer.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.customer.dto.SearchCustomerResponse
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/customer/SearchCustomerEndpoint.sql"])
class SearchCustomerEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun all() {
        val response = rest.getForEntity("/v1/customers", SearchCustomerResponse::class.java)

        val customers = response.body!!.customers
        assertEquals(listOf(1L, 2L, 3L), customers.map { it.id })

        val customer = customers[0]
        assertEquals("+23761111111", customer.whatsappNumber)
        assertEquals(100L, customer.party.id)
        assertEquals("Ray", customer.party.firstName)
        assertNull(customers[2].whatsappNumber)
    }

    @Test
    fun `by ids`() {
        val response = rest.getForEntity("/v1/customers?id=1&id=3", SearchCustomerResponse::class.java)

        val customers = response.body!!.customers
        assertEquals(listOf(1L, 3L), customers.map { it.id })
    }

    @Test
    fun `by party ids`() {
        val response = rest.getForEntity("/v1/customers?party-id=101&party-id=102", SearchCustomerResponse::class.java)

        val customers = response.body!!.customers
        assertEquals(listOf(2L, 3L), customers.map { it.id })
        assertEquals(listOf(101L, 102L), customers.map { it.party.id })
    }

    @Test
    fun `no match`() {
        val response = rest.getForEntity("/v1/customers?id=999", SearchCustomerResponse::class.java)

        assertEquals(0, response.body!!.customers.size)
    }

    @Test
    fun limit() {
        val response = rest.getForEntity("/v1/customers?limit=2", SearchCustomerResponse::class.java)

        assertEquals(listOf(1L, 2L), response.body!!.customers.map { it.id })
    }

    @Test
    fun offset() {
        val response = rest.getForEntity("/v1/customers?offset=1", SearchCustomerResponse::class.java)

        assertEquals(listOf(2L, 3L), response.body!!.customers.map { it.id })
    }

    @Test
    fun `limit and offset`() {
        val response = rest.getForEntity("/v1/customers?limit=1&offset=1", SearchCustomerResponse::class.java)

        assertEquals(listOf(2L), response.body!!.customers.map { it.id })
    }

    @Test
    fun `customer from another tenant is not returned`() {
        val response = rest.getForEntity("/v1/customers?id=4", SearchCustomerResponse::class.java)

        assertTrue(response.body!!.customers.isEmpty())
    }

    @Test
    fun `switching tenant context reveals only that tenant's customer`() {
        overrideTenantId = 2L

        val response = rest.getForEntity("/v1/customers", SearchCustomerResponse::class.java)

        assertEquals(listOf(4L), response.body!!.customers.map { it.id })
    }
}
