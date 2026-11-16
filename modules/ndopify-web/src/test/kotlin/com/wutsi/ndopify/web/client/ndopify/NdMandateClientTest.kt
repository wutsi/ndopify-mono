package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
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

class NdMandateClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdMandateClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdMandateClient("http://localhost:8080", builder)
    }

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/7"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    JsonMapper.builder().build().writeValueAsString(GetMandateResponse(Mandate(id = 7))),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(7L, client.get(7).id)
        server.verify()
    }

    @Test
    fun `get - forbidden`() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/7")).andRespond(withStatus(HttpStatus.FORBIDDEN))

        assertFailsWith<HttpClientErrorException.Forbidden> { client.get(7) }
    }

    @Test
    fun create() {
        server.expect(requestTo("http://localhost:8080/v1/mandates"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.originListingId").value(123))
            .andExpect(jsonPath("$.mandateType").value("SEARCH"))
            .andExpect(jsonPath("$.firstName").value("Awa"))
            .andExpect(jsonPath("$.movingPeriod").value("WITHIN_1_MONTH"))
            .andRespond(
                withSuccess(
                    JsonMapper.builder().build().writeValueAsString(CreateMandateResponse(mandateId = 9)),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val id = client.create(
            CreateMandateRequest(
                mandateType = MandateType.SEARCH,
                originListingId = 123,
                firstName = "Awa",
                movingPeriod = MovingPeriod.WITHIN_1_MONTH,
            ),
        )

        assertEquals(9L, id)
        server.verify()
    }

    @Test
    fun `create - rate limited`() {
        server.expect(requestTo("http://localhost:8080/v1/mandates")).andRespond(withStatus(HttpStatus.CONFLICT))

        assertFailsWith<HttpClientErrorException.Conflict> { client.create(CreateMandateRequest()) }
    }
}
