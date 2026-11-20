package com.wutsi.ndopify.platform.storage

import com.wutsi.ndopify.refdata.dto.StorageType
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.net.URLDecoder

data class StoredObject(val contentType: String?, val contentLength: Long)

interface StorageService {
    fun type(): StorageType

    fun store(
        path: String,
        content: InputStream,
        contentType: String?
    )

    fun get(path: String, os: OutputStream)

    fun generatePresignedUrl(path: String, expirationInSeconds: Int): URL

    /**
     * A short-lived URL to PUT [path] with [contentType]. Null when this storage can't presign an upload (ex: local):
     * the caller falls back to uploading the file through the server.
     */
    fun presignedUploadUrl(path: String, contentType: String, expirationInSeconds: Int): URL? = null

    fun metadata(path: String): StoredObject?

    fun readPrefix(path: String, maxBytes: Int): ByteArray

    fun delete(path: String)

    /**
     * The file's permanent URL, for files meant to be public (ex: a profile photo shown in e-mails). It only
     * works if the storage lets anybody read [path]: on S3, the bucket policy must allow it for that prefix.
     */
    fun publicUrl(path: String): URL

    /**
     * The path of a file from its [publicUrl], the inverse of [publicUrl]: null for a URL that isn't one of this
     * storage's (another host, bucket or servlet path).
     */
    fun pathOf(url: String): String? {
        val prefix = publicUrl("").toString()
        return url.takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.ifBlank { null }
            ?.let { path -> URLDecoder.decode(path, Charsets.UTF_8) }
    }
}
