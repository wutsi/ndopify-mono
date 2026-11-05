package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.customer.dto.GetCustomerResponse
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdCustomerClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdCustomerClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdCustomerClient("http://localhost:8080", builder)
    }

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/customers/42"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(GetCustomerResponse(Customer(id = 42, whatsappNumber = "+237699887766"))),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals("+237699887766", client.get(42).whatsappNumber)
    }

    @Test
    fun `get - not found`() {
        server.expect(requestTo("http://localhost:8080/v1/customers/42")).andRespond(withStatus(HttpStatus.NOT_FOUND))
        assertFailsWith<HttpClientErrorException.NotFound> { client.get(42) }
    }

    private fun json(value: Any): String = JsonMapper.builder().build().writeValueAsString(value)
}
