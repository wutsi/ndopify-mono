package com.wutsi.ndopify.platform.storage.local

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
    @TempDir
    lateinit var tmpDir: File

    private lateinit var service: StorageServiceLocal

    @Test
    fun store() {
        service = StorageServiceLocal(tmpDir.absolutePath)
        val content = "hello-world".toByteArray()

        service.store("foo/bar.txt", ByteArrayInputStream(content), "text/plain")

        val file = File(tmpDir, "foo/bar.txt")
        assertTrue(file.exists())
        assertEquals("hello-world", file.readText())
    }

    @Test
    fun `store creates missing parent directories`() {
        service = StorageServiceLocal(tmpDir.absolutePath)

        service.store("a/b/c/bar.txt", ByteArrayInputStream("xyz".toByteArray()), "text/plain")

        assertTrue(File(tmpDir, "a/b/c/bar.txt").exists())
    }

    @Test
    fun `store with leading slash path`() {
        service = StorageServiceLocal(tmpDir.absolutePath)

        service.store("/foo/bar.txt", ByteArrayInputStream("content".toByteArray()), "text/plain")

        assertTrue(File(tmpDir, "foo/bar.txt").exists())
    }

    @Test
    fun get() {
        service = StorageServiceLocal(tmpDir.absolutePath)
        val file = File(tmpDir, "foo/bar.txt")
        file.parentFile.mkdirs()
        file.writeText("hello-world")

        val os = ByteArrayOutputStream()
        service.get("foo/bar.txt", os)

        assertEquals("hello-world", os.toString())
    }

    @Test
    fun `get throws when file does not exist`() {
        service = StorageServiceLocal(tmpDir.absolutePath)

        assertFailsWith<FileNotFoundException> {
            service.get("missing.txt", ByteArrayOutputStream())
        }
    }
}
