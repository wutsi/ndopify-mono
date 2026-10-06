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
}
