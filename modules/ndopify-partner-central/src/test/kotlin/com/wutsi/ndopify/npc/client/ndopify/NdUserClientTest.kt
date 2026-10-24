package com.wutsi.ndopify.npc.client.ndopify

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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NdUserClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdUserClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdUserClient("http://localhost:8080", builder)
    }

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/users/11"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"user":{"id":11,"email":"ray@gmail.com","party":{"id":100,"firstName":"Ray","lastName":"Sponsible","email":"ray@gmail.com","kycStatus":"PENDING_VERIFICATION","createdAt":0,"modifiedAt":0},"createdAt":0}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val user = client.get(11)

        assertEquals(11L, user?.id)
        assertEquals(100L, user?.party?.id)
        assertEquals("Ray", user?.party?.firstName)
        server.verify()
    }

    @Test
    fun `get unknown user`() {
        server.expect(requestTo("http://localhost:8080/v1/users/11"))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":{"code":"urn:wutsi:ndopify:error:user:not-found"}}"""),
            )

        assertNull(client.get(11))
    }

    @Test
    fun `get with another error`() {
        server.expect(requestTo("http://localhost:8080/v1/users/11"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        assertFailsWith<HttpClientErrorException> { client.get(11) }
    }
}
