package com.wutsi.ndopify.web.config

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseActions
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Clock
import java.time.Duration

class RestConfigurationTest {
    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `sends the access token of the cookie`() {
        val server = call(Cookie("__nweb_access_token", "abc")) { server ->
            server.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
        }
        server.verify()
    }

    @Test
    fun `sends no Authorization without a cookie`() {
        val server = call(null) { server -> server.andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION)) }
        server.verify()
    }

    private fun call(
        cookie: Cookie?,
        expect: (ResponseActions) -> ResponseActions,
    ): MockRestServiceServer {
        val request = MockHttpServletRequest().apply { cookie?.let { setCookies(it) } }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

        val core = WebCoreConfiguration(Clock.systemUTC())
        val config = RestConfiguration(Duration.ofSeconds(1), Duration.ofSeconds(1))
        val builder = RestClient.builder()
        config.authorizationRestClientCustomizer(core.accessTokenService(request, MockHttpServletResponse()))
            .customize(builder)
        val server = MockRestServiceServer.bindTo(builder).build()
        expect(server.expect(requestTo("http://x/v1/y"))).andRespond(withSuccess())
        builder.build().get().uri("http://x/v1/y").retrieve().toBodilessEntity()
        return server
    }
}
