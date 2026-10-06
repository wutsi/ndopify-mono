package com.wutsi.ndopify.platform.storage.local

import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StorageServiceLocalTest {
    val servletPath = "/storage"
    val port = 8080

    @TempDir
    lateinit var tmpDir: File
    private lateinit var service: StorageServiceLocal

    @BeforeEach
    fun setUp() {
        service = StorageServiceLocal(tmpDir.absolutePath, servletPath, port)
    }

    @Test
    fun type() {
        assertEquals(StorageType.LOCAL, service.type())
    }

    @Test
    fun store() {
        val content = "hello-world".toByteArray()

        service.store("foo/bar.txt", ByteArrayInputStream(content), "text/plain")

        val file = File(tmpDir, "foo/bar.txt")
        assertTrue(file.exists())
        assertEquals("hello-world", file.readText())
    }

    @Test
    fun `store creates missing parent directories`() {
        service.store("a/b/c/bar.txt", ByteArrayInputStream("xyz".toByteArray()), "text/plain")

        assertTrue(File(tmpDir, "a/b/c/bar.txt").exists())
    }

    @Test
    fun `store with leading slash path`() {
        service.store("/foo/bar.txt", ByteArrayInputStream("content".toByteArray()), "text/plain")

        assertTrue(File(tmpDir, "foo/bar.txt").exists())
    }

    @Test
    fun get() {
        val file = File(tmpDir, "foo/bar.txt")
        file.parentFile.mkdirs()
        file.writeText("hello-world")

        val os = ByteArrayOutputStream()
        service.get("foo/bar.txt", os)

        assertEquals("hello-world", os.toString())
    }

    @Test
    fun `get throws when file does not exist`() {
        assertFailsWith<FileNotFoundException> {
            service.get("missing.txt", ByteArrayOutputStream())
        }
    }

    @Test
    fun `presigned url`() {
        val url = service.generatePresignedUrl("foo/bar.txt", 3600)

        assertEquals("http://localhost:8080/storage/foo/bar.txt", url.toString())
    }
}
