package com.wutsi.ndopify.platform.storage.local

import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StoredObject
import com.wutsi.ndopify.platform.util.MimeUtils
import com.wutsi.ndopify.refdata.dto.StorageType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL

class StorageServiceLocal(
    private val directory: String,
    private val servletPath: String,
    private val port: Int,
) : StorageService {
    companion object {
        const val BUF_SIZE = 10 * 1024
    }

    override fun type(): StorageType {
        return StorageType.LOCAL
    }

    override fun store(path: String, content: InputStream, contentType: String?) {
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

    override fun generatePresignedUrl(path: String, expirationInSeconds: Int): URL {
        return publicUrl(path)
    }

    override fun publicUrl(path: String): URL {
        return URL("http://localhost:$port/${servletPath.removePrefix("/")}/${path.removePrefix("/")}")
    }

    override fun metadata(path: String): StoredObject? {
        val file = toFile(path)
        return if (file.isFile) StoredObject(MimeUtils.getMimeTypeFromExtension(file.extension), file.length()) else null
    }

    override fun readPrefix(path: String, maxBytes: Int): ByteArray {
        val file = toFile(path)
        return if (file.isFile) FileInputStream(file).use { it.readNBytes(maxBytes) } else ByteArray(0)
    }

    override fun delete(path: String) {
        toFile(path).delete()
    }

    private fun toFile(path: String) = File("$directory/" + path.removePrefix("/"))
}
