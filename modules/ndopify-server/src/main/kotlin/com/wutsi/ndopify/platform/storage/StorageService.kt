package com.wutsi.ndopify.platform.storage

import com.wutsi.ndopify.refdata.dto.StorageType
import java.io.InputStream
import java.io.OutputStream
import java.net.URL

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
     * The file's permanent URL, for files meant to be public (ex: a profile photo shown in e-mails). It only
     * works if the storage lets anybody read [path]: on S3, the bucket policy must allow it for that prefix.
     */
    fun publicUrl(path: String): URL
}
