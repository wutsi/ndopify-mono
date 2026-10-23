package com.wutsi.ndopify.customer.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.customer.dto.GetCustomerResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/customer/GetCustomerEndpoint.sql"])
class GetCustomerEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun get() {
        val response = rest.getForEntity("/v1/customers/1", GetCustomerResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val customer = response.body!!.customer
        assertEquals(1L, customer.id)
        assertEquals("+23761111111", customer.whatsappNumber)

        val party = customer.party
        assertEquals(100L, party.id)
        assertEquals("Ray", party.firstName)
        assertEquals("Sponsible", party.lastName)
        assertEquals("ray.sponsible@gmail.com", party.email)
    }

    @Test
    fun `customer not found`() {
        val response = rest.getForEntity("/v1/customers/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CUSTOMER_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `customer from another tenant is not found`() {
        val response = rest.getForEntity("/v1/customers/2", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CUSTOMER_NOT_FOUND, response.body?.error?.code)
    }
}
