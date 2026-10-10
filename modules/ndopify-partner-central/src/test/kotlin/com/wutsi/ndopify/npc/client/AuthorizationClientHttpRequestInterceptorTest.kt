package com.wutsi.ndopify.npc.client

import com.wutsi.ndopify.npc.service.security.AccessTokenService.Companion.ACCESS_TOKEN_COOKIE
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

class AuthorizationClientHttpRequestInterceptorTest {
    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `adds the access token from the cookie`() {
        val (server, rest) = client { incoming(Cookie(ACCESS_TOKEN_COOKIE, "token-123")) }
        server.expect(requestTo("http://localhost:8080/v1/agents"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        rest.get().uri("/v1/agents").retrieve().body<String>()

        server.verify()
    }

    @Test
    fun `keeps the Authorization header set by the caller`() {
        val (server, rest) = client { incoming(Cookie(ACCESS_TOKEN_COOKIE, "token-123")) }
        server.expect(requestTo("http://localhost:8080/v1/agents"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer other"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        rest.get().uri("/v1/agents").header(HttpHeaders.AUTHORIZATION, "Bearer other").retrieve().body<String>()

        server.verify()
    }

    @Test
    fun `no header when not signed in`() {
        val (server, rest) = client { incoming(Cookie("other", "value")) }
        server.expect(requestTo("http://localhost:8080/v1/agents"))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        rest.get().uri("/v1/agents").retrieve().body<String>()

        server.verify()
    }

    @Test
    fun `no header when the cookie is empty`() {
        val (server, rest) = client { incoming(Cookie(ACCESS_TOKEN_COOKIE, "")) }
        server.expect(requestTo("http://localhost:8080/v1/agents"))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        rest.get().uri("/v1/agents").retrieve().body<String>()

        server.verify()
    }

    @Test
    fun `no header outside a web request`() {
        val (server, rest) = client { null }
        server.expect(requestTo("http://localhost:8080/v1/agents"))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        rest.get().uri("/v1/agents").retrieve().body<String>()

        server.verify()
    }

    @Test
    fun `reads the current request from RequestContextHolder by default`() {
        RequestContextHolder.setRequestAttributes(
            ServletRequestAttributes(incoming(Cookie(ACCESS_TOKEN_COOKIE, "token-456"))),
        )
        val builder = RestClient.builder().requestInterceptor(AuthorizationClientHttpRequestInterceptor())
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("http://localhost:8080/v1/agents"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-456"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        builder.baseUrl("http://localhost:8080").build().get().uri("/v1/agents").retrieve().body<String>()

        server.verify()
    }

    private fun incoming(vararg cookies: Cookie) = MockHttpServletRequest().apply { setCookies(*cookies) }

    private fun client(currentRequest: () -> HttpServletRequest?): Pair<MockRestServiceServer, RestClient> {
        val builder = RestClient.builder().requestInterceptor(AuthorizationClientHttpRequestInterceptor(currentRequest))
        val server = MockRestServiceServer.bindTo(builder).build()
        return server to builder.baseUrl("http://localhost:8080").build()
    }
}
