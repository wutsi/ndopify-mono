package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.GetContractResponse
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
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdContractClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdContractClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdContractClient("http://localhost:8080", builder)
    }

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/contracts/abc"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    JsonMapper.builder().build().writeValueAsString(
                        GetContractResponse(
                            Contract(id = "abc", status = ContractStatus.PENDING_SIGNATURE, signatureDeadline = Date(4102444800000)),
                        ),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val contract = client.get("abc")

        server.verify()
        assertEquals("abc", contract.id)
        assertEquals(ContractStatus.PENDING_SIGNATURE, contract.status)
        assertEquals(4102444800000L, contract.signatureDeadline?.time)
    }

    @Test
    fun `get - not found`() {
        server.expect(requestTo("http://localhost:8080/v1/contracts/abc"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFailsWith<HttpClientErrorException> { client.get("abc") }
    }
}
