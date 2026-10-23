package com.wutsi.ndopify.platform.util.http

import tools.jackson.databind.json.JsonMapper
import java.net.ServerSocket
import java.net.http.HttpClient
import java.net.http.HttpTimeoutException
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class HttpTest {
    // Accepts connections but never answers
    private lateinit var server: ServerSocket

    @BeforeTest
    fun setUp() {
        server = ServerSocket(0)
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun http(readTimeout: Duration?) = Http(
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
        objectMapper = JsonMapper(),
        readTimeout = readTimeout,
    )

    private fun url() = "http://localhost:${server.localPort}/test"

    @Test
    fun `get times out`() {
        assertFailsWith<HttpTimeoutException> {
            http(Duration.ofMillis(200)).get(url(), Map::class.java, emptyMap())
        }
    }

    @Test
    fun `post times out`() {
        assertFailsWith<HttpTimeoutException> {
            http(Duration.ofMillis(200)).post(url(), mapOf("a" to "b"), Map::class.java, emptyMap())
        }
    }
}
