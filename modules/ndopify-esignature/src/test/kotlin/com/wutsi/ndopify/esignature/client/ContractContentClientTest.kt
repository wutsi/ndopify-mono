package com.wutsi.ndopify.esignature.client

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContractContentClientTest {
    private val url = "https://storage.example.com/contracts/abc.html"
    private lateinit var server: MockRestServiceServer
    private lateinit var client: ContractContentClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = ContractContentClient(builder.build())
    }

    @Test
    fun `returns body html`() {
        server.expect(requestTo(url))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    "<html><head><title>x</title></head><body><h4>Article 1</h4><p>Hello</p></body></html>",
                    MediaType.TEXT_HTML,
                ),
            )

        val html = client.fetch(url)

        server.verify()
        assertEquals("<h4>Article 1</h4><p>Hello</p>", html?.replace(Regex(">\\s+<"), "><")?.trim())
    }

    @Test
    fun `script is stripped`() {
        server.expect(requestTo(url))
            .andRespond(
                withSuccess(
                    """<body><script>alert(1)</script><h4 onclick="evil()">Title</h4><ul><li>One</li></ul></body>""",
                    MediaType.TEXT_HTML,
                ),
            )

        val html = client.fetch(url)

        assertTrue(html != null)
        assertFalse(html.contains("script"))
        assertFalse(html.contains("alert"))
        assertFalse(html.contains("onclick"))
        assertTrue(html.contains("<h4>Title</h4>"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<li>One</li>"))
    }

    @Test
    fun `null on 404`() {
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertNull(client.fetch(url))
    }

    @Test
    fun `null on non html`() {
        server.expect(requestTo(url)).andRespond(withSuccess("%PDF-1.4", MediaType.APPLICATION_PDF))

        assertNull(client.fetch(url))
    }

    @Test
    fun `no authorization header`() {
        server.expect(requestTo(url))
            .andExpect(headerDoesNotExist("Authorization"))
            .andExpect(headerDoesNotExist("X-Tenant-ID"))
            .andRespond(withSuccess("<body><p>ok</p></body>", MediaType.TEXT_HTML))

        assertEquals("<p>ok</p>", client.fetch(url))
        server.verify()
    }
}
