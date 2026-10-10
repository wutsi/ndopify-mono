package com.wutsi.ndopify.npc.client

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LoggingClientHttpRequestInterceptorTest {
    private val logger = mock<Logger>()
    private lateinit var server: MockRestServiceServer
    private lateinit var rest: RestClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().requestInterceptor(LoggingClientHttpRequestInterceptor(logger))
        server = MockRestServiceServer.bindTo(builder).build()
        rest = builder.baseUrl("http://localhost:8080").build()
    }

    @Test
    fun logsMethodUrlAndLatency() {
        server.expect(requestTo("http://localhost:8080/v1/tenants?active=true"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        rest.get().uri("/v1/tenants?active=true").retrieve().body<String>()

        val message = argumentCaptor<String>()
        verify(logger).info(message.capture())
        assertTrue(
            Regex("""^>>> GET http://localhost:8080/v1/tenants\?active=true 200 OK \[\d+ms]$""")
                .matches(message.firstValue),
            message.firstValue,
        )
    }

    @Test
    fun logsFailedCall() {
        server.expect(requestTo("http://localhost:8080/v1/tenants"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertFailsWith<HttpServerErrorException> {
            rest.post().uri("/v1/tenants").retrieve().body<String>()
        }

        val message = argumentCaptor<String>()
        verify(logger).info(message.capture())
        assertTrue(
            Regex("""^>>> POST http://localhost:8080/v1/tenants 500 INTERNAL_SERVER_ERROR \[\d+ms]$""")
                .matches(message.firstValue),
            message.firstValue,
        )
    }
}
