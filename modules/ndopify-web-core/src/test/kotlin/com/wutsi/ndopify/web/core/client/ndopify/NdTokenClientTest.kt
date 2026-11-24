package com.wutsi.ndopify.web.core.client.ndopify

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdTokenClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdTokenClient

    @BeforeEach
    fun setUp() {
        val rest = RestTemplate()
        server = MockRestServiceServer.bindTo(rest).build()
        client = NdTokenClient("http://localhost:8080", rest)
    }

    @Test
    fun refresh() {
        server.expect(requestTo("http://localhost:8080/v1/auth/refresh"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer old-token"))
            .andExpect(headerDoesNotExist("X-Tenant-ID"))
            .andRespond(withSuccess("""{"accessToken":"new-token"}""", MediaType.APPLICATION_JSON))

        assertEquals("new-token", client.refresh("old-token"))
        server.verify()
    }

    @Test
    fun `a refused renewal`() {
        server.expect(requestTo("http://localhost:8080/v1/auth/refresh"))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        assertFailsWith<HttpClientErrorException> { client.refresh("old-token") }
    }

    @Test
    fun `an answer without a body`() {
        server.expect(requestTo("http://localhost:8080/v1/auth/refresh"))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))

        assertEquals("", client.refresh("old-token"))
    }
}
