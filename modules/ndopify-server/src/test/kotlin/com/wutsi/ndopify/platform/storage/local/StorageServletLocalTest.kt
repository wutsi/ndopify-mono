package com.wutsi.ndopify.platform.storage.local

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals

class StorageServletLocalTest {
    @TempDir
    lateinit var tmpDir: File

    private lateinit var servlet: StorageServletLocal
    private lateinit var request: HttpServletRequest
    private lateinit var response: HttpServletResponse
    private lateinit var out: ByteArrayOutputStream

    @BeforeEach
    fun setUp() {
        servlet = StorageServletLocal(tmpDir.absolutePath)
        request = mock()
        response = mock()

        out = ByteArrayOutputStream()
        val servletOutputStream = object : ServletOutputStream() {
            override fun write(b: Int) = out.write(b)

            override fun isReady() = true

            override fun setWriteListener(writeListener: WriteListener?) = Unit
        }
        doReturn(servletOutputStream).whenever(response).outputStream
        doReturn("GET").whenever(request).method
    }

    @Test
    fun get() {
        File(tmpDir, "foo").mkdirs()
        File(tmpDir, "foo/bar.txt").writeText("hello-world")
        doReturn("/foo/bar.txt").whenever(request).pathInfo

        servlet.service(request, response)

        assertEquals("hello-world", out.toString())
        verify(response).contentType = "text/plain"
    }

    @Test
    fun `file not found`() {
        doReturn("/missing.txt").whenever(request).pathInfo

        servlet.service(request, response)

        verify(response).sendError(404)
    }

    @Test
    fun `unexpected error`() {
        File(tmpDir, "broken.txt").writeText("hello-world")
        doReturn("/broken.txt").whenever(request).pathInfo

        val failingOutputStream = object : ServletOutputStream() {
            override fun write(b: Int): Unit = throw IOException("boom")

            override fun isReady() = true

            override fun setWriteListener(writeListener: WriteListener?) = Unit
        }
        doReturn(failingOutputStream).whenever(response).outputStream

        servlet.service(request, response)

        verify(response).sendError(500)
    }
}
