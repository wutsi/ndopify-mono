package com.wutsi.ndopify.platform.storage.local

import com.wutsi.ndopify.platform.storage.StorageService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class StorageServiceLocal(
    private val directory: String
) : StorageService {
    companion object {
        const val BUF_SIZE = 10 * 1024
    }

    override fun store(path: String, content: InputStream, contentType: String) {
        val file = toFile(path)
        file.parentFile.mkdirs()

        FileOutputStream(file)
            .use {
                content.copyTo(it, BUF_SIZE)
            }
    }

    override fun get(path: String, os: OutputStream) {
        val file = toFile(path)
        val fis = FileInputStream(file)
        fis.use {
            fis.copyTo(os, BUF_SIZE)
        }
    }

    private fun toFile(path: String) = File("$directory/" + path.removePrefix("/"))
}
