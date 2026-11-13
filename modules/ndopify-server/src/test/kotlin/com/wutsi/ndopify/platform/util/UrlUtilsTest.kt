package com.wutsi.ndopify.platform.util

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URL

/**
 * A local HTTP server keeps the tests independent from external image/PDF hosts: those made the main build flaky
 * (e.g. picsum.photos answering 522).
 */
class UrlUtilsTest {
    private lateinit var server: HttpServer

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/image") { exchange ->
            exchange.responseHeaders.add("Content-Type", "image/jpeg")
            exchange.sendResponseHeaders(200, IMAGE.size.toLong())
            exchange.responseBody.use { it.write(IMAGE) }
        }
        server.createContext("/pdf") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/pdf")
            exchange.sendResponseHeaders(200, PDF.size.toLong())
            exchange.responseBody.use { it.write(PDF) }
        }
        server.createContext("/missing") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `download image`() {
        val file = UrlUtils.download(url("/image"))
        assertEquals("jpg", file.extension)
    }

    @Test
    fun `download pdf`() {
        val file = UrlUtils.download(url("/pdf"))
        assertEquals("pdf", file.extension)
    }

    @Test
    fun `not found`() {
        assertThrows<IOException> {
            UrlUtils.download(url("/missing"))
        }
    }

    private fun url(path: String): URL = URL("http://127.0.0.1:${server.address.port}$path")

    companion object {
        private val IMAGE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        private val PDF = "%PDF-1.4".toByteArray()
    }
}
