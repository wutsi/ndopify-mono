package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractSummary
import com.wutsi.ndopify.contract.dto.GetContractResponse
import com.wutsi.ndopify.contract.dto.SearchContractRequest
import com.wutsi.ndopify.contract.dto.SearchContractResponse
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
import org.springframework.web.client.HttpServerErrorException
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

    @Test
    fun search() {
        server.expect(
            requestTo(
                "http://localhost:8080/v1/contracts?id=c-1&id=c-2&status=SIGNED&status=PENDING_SIGNATURE&party-id=100&limit=2&offset=0",
            ),
        )
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    JsonMapper.builder().build().writeValueAsString(
                        SearchContractResponse(
                            listOf(
                                ContractSummary(id = "c-2", status = ContractStatus.SIGNED, validUntil = Date(4102444800000)),
                                ContractSummary(id = "c-1", status = ContractStatus.PENDING_SIGNATURE),
                            ),
                        ),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val contracts = client.search(
            SearchContractRequest(
                ids = listOf("c-1", "c-2"),
                statuses = listOf(ContractStatus.SIGNED, ContractStatus.PENDING_SIGNATURE),
                partyIds = listOf(100),
                limit = 2,
            ),
        )

        server.verify()
        assertEquals(listOf("c-2", "c-1"), contracts.map { it.id })
        assertEquals(4102444800000L, contracts[0].validUntil?.time)
    }

    @Test
    fun `search - error`() {
        server.expect(requestTo("http://localhost:8080/v1/contracts?id=c-1&limit=20&offset=0"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertFailsWith<HttpServerErrorException> { client.search(SearchContractRequest(ids = listOf("c-1"))) }
    }
}
