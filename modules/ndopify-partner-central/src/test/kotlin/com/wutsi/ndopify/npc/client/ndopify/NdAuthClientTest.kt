package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.AuthenticateRequest
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
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdAuthClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdAuthClient

    private val request = AuthenticateRequest(
        email = "ray@gmail.com",
        applicationCode = "partner-central",
        authType = AuthType.OTP,
        secret = "123456",
    )

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdAuthClient("http://localhost:8080", builder)
    }

    @Test
    fun authenticate() {
        server.expect(requestTo("http://localhost:8080/v1/auth"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().json(
                    """{"email":"ray@gmail.com","applicationCode":"partner-central","authType":"OTP","secret":"123456"}""",
                ),
            )
            .andRespond(withSuccess("""{"accessToken":"token-123"}""", MediaType.APPLICATION_JSON))

        val response = client.authenticate(request)

        server.verify()
        assertEquals("token-123", response.accessToken)
    }

    @Test
    fun invalidCredentials() {
        server.expect(requestTo("http://localhost:8080/v1/auth"))
            .andRespond(
                withStatus(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":{"code":"urn:wutsi:ndopify:error:auth:invalid-credentials"}}"""),
            )

        val ex = assertFailsWith<HttpClientErrorException.Conflict> { client.authenticate(request) }
        // LoginController reads the error code this way to pick its message.
        assertEquals("urn:wutsi:ndopify:error:auth:invalid-credentials", ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code)
    }
}
