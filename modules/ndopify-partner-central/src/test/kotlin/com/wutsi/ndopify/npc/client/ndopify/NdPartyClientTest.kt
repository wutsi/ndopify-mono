package com.wutsi.ndopify.npc.client.ndopify

import org.hamcrest.Matchers.containsString
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
import kotlin.test.assertFailsWith

class NdPartyClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdPartyClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdPartyClient("http://localhost:8080", builder)
    }

    @Test
    fun uploadPhoto() {
        server.expect(requestTo("http://localhost:8080/v1/parties/100/photo"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andExpect(content().string(containsString("name=\"multipartFile\"")))
            .andExpect(content().string(containsString("Content-Type: image/png")))
            .andRespond(withSuccess())

        client.uploadPhoto(100, byteArrayOf(1, 2, 3), "image/png")
        server.verify()
    }

    @Test
    fun `uploadPhoto - rejected`() {
        server.expect(requestTo("http://localhost:8080/v1/parties/100/photo"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        assertFailsWith<HttpClientErrorException> { client.uploadPhoto(100, byteArrayOf(1), "image/jpeg") }
    }
}
