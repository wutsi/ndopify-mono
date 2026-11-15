package com.wutsi.ndopify.platform.util.http

import com.sun.net.httpserver.HttpServer
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.http.HttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A local server captures the request, so the tests assert the `multipart/form-data` wire format itself. */
class HttpMultipartTest {
    private lateinit var server: HttpServer
    private var contentType: String? = null
    private var body: ByteArray = ByteArray(0)

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/multipart") { exchange ->
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            body = exchange.requestBody.readBytes()
            val response = """{"ok":true}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
    }

    @AfterTest
    fun tearDown() = server.stop(0)

    @Test
    fun `postMultipart sends its fields and its file`() {
        val http = Http(HttpClient.newBuilder().build(), JsonMapper())

        val response = http.postMultipart(
            "http://127.0.0.1:${server.address.port}/multipart",
            mapOf(
                "chat_id" to 1000L,
                "caption" to "En ligne",
                "reply_markup" to mapOf("inline_keyboard" to emptyList<List<Any>>()),
            ),
            HttpFile("photo", "1.jpg", "image/jpeg", byteArrayOf(1, 2, 3)),
            Map::class.java,
            emptyMap(),
        )

        assertTrue(contentType!!.startsWith("multipart/form-data; boundary="))
        val text = String(body, Charsets.UTF_8)
        assertTrue(text.contains("""name="chat_id""""))
        assertTrue(text.contains("1000"))
        assertTrue(text.contains("""name="caption""""))
        assertTrue(text.contains("En ligne"))
        assertTrue(text.contains("""name="reply_markup""""))
        assertTrue(text.contains("""{"inline_keyboard":[]}"""))
        assertTrue(text.contains("""name="photo"; filename="1.jpg""""))
        assertTrue(text.contains("Content-Type: image/jpeg"))
        assertTrue(text.contains(String(byteArrayOf(1, 2, 3), Charsets.ISO_8859_1)))
        assertEquals(true, response!!["ok"])
    }

    @Test
    fun `postMultipart throws on a non-2xx answer`() {
        server.createContext("/error") { exchange ->
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        val http = Http(HttpClient.newBuilder().build(), JsonMapper())

        assertFailsWith<HttpException> {
            http.postMultipart(
                "http://127.0.0.1:${server.address.port}/error",
                emptyMap(),
                HttpFile("photo", "1.jpg", "image/jpeg", byteArrayOf(1)),
                Map::class.java,
                emptyMap(),
            )
        }
    }
}
