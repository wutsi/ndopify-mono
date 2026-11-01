package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.customer.dto.GetCustomerResponse
import com.wutsi.ndopify.customer.dto.SearchCustomerResponse
import com.wutsi.ndopify.party.dto.Party
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
        server.expect(requestTo("http://localhost:8080/v1/customers/5"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    JsonMapper.builder().build().writeValueAsString(
                        GetCustomerResponse(
                            Customer(id = 5, party = Party(firstName = "Ray", email = "ray@gmail.com"), whatsappNumber = "+237699123456"),
                        ),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val customer = client.get(5)

        server.verify()
        assertEquals(5L, customer.id)
        assertEquals("ray@gmail.com", customer.party.email)
        assertEquals("+237699123456", customer.whatsappNumber)
    }

    @Test
    fun `get - not found`() {
        server.expect(requestTo("http://localhost:8080/v1/customers/5"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFailsWith<HttpClientErrorException> { client.get(5) }
    }

    @Test
    fun search() {
        server.expect(requestTo("http://localhost:8080/v1/customers?id=14&id=17&limit=2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    JsonMapper.builder().build().writeValueAsString(
                        SearchCustomerResponse(listOf(CustomerSummary(id = 14, party = Party(firstName = "Sandrine")))),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val customers = client.search(listOf(14, 17))

        server.verify()
        assertEquals(listOf(14L), customers.map { it.id })
        assertEquals("Sandrine", customers[0].party.firstName)
    }

    @Test
    fun `search - none`() {
        assertEquals(emptyList(), client.search(emptyList()))
        server.verify()
    }
}
