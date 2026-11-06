package com.wutsi.ndopify.npc.config

import com.wutsi.ndopify.web.core.client.AuthorizationClientHttpRequestInterceptor
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.concurrent.Callable
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParallelTest {
    private val parallel = Parallel()

    @AfterEach
    fun close() = parallel.close()

    @Test
    fun `binds the caller's request attributes`() {
        val request = MockHttpServletRequest().apply { setAttribute("npc.test", "yes") }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        try {
            val seen = parallel.async {
                RequestContextHolder.getRequestAttributes()
                    ?.getAttribute("npc.test", RequestAttributes.SCOPE_REQUEST)
            }.join()

            assertEquals("yes", seen)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    fun `sends the agent's access token`() {
        val builder = RestClient.builder().baseUrl("http://localhost:8080")
        val server = MockRestServiceServer.bindTo(builder).build()
        val rest = builder
            .requestInterceptor(AuthorizationClientHttpRequestInterceptor("__npc_access_token"))
            .build()
        val request = MockHttpServletRequest().apply {
            setCookies(Cookie("__npc_access_token", "token-123"))
        }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        try {
            server.expect(requestTo("http://localhost:8080/v1/agents/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

            parallel.async { rest.get().uri("/v1/agents/me").retrieve().body<String>() }.join()

            server.verify()
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    fun `runs the tasks at the same time`() {
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)

        val first = parallel.async {
            started.countDown()
            assertTrue(release.await(2, TimeUnit.SECONDS))
            1
        }
        val second = parallel.async {
            started.countDown()
            assertTrue(release.await(2, TimeUnit.SECONDS))
            2
        }

        assertTrue(started.await(2, TimeUnit.SECONDS), "both tasks must start before either finishes")
        release.countDown()
        assertEquals(1, first.join())
        assertEquals(2, second.join())
    }

    @Test
    fun `clears the request attributes after the task`() {
        val executor = Executors.newSingleThreadExecutor()
        val single = Parallel(executor)
        try {
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest()))
            single.async { Unit }.join()
            RequestContextHolder.resetRequestAttributes()

            val leaked = executor.submit(Callable { RequestContextHolder.getRequestAttributes() }).get()

            assertNull(leaked)
        } finally {
            single.close()
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    fun `propagates the failure`() {
        val failure = assertFailsWith<CompletionException> {
            parallel.async<Unit> { throw IllegalStateException("boom") }.join()
        }

        assertEquals("boom", failure.cause?.message)
    }
}
