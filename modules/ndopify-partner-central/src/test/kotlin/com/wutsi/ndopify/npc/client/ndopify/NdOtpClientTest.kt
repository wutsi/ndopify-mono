package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.security.dto.CreateOtpRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import kotlin.test.assertFailsWith

class NdOtpClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdOtpClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdOtpClient("http://localhost:8080", builder)
    }

    @Test
    fun create() {
        server.expect(requestTo("http://localhost:8080/v1/otp"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""{"email":"ray@gmail.com","ttl":300}""", true))
            .andRespond(withSuccess())

        client.create(CreateOtpRequest(email = "ray@gmail.com"))

        server.verify()
    }

    @Test
    fun badRequest() {
        server.expect(requestTo("http://localhost:8080/v1/otp"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        assertFailsWith<HttpClientErrorException.BadRequest> { client.create(CreateOtpRequest(email = "")) }
    }

    @Test
    fun serverError() {
        server.expect(requestTo("http://localhost:8080/v1/otp"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertFailsWith<HttpServerErrorException> { client.create(CreateOtpRequest(email = "ray@gmail.com")) }
    }
}
