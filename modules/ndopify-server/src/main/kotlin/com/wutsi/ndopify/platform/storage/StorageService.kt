package com.wutsi.ndopify.platform.storage

import java.io.InputStream
import java.io.OutputStream

interface StorageService {
    fun store(
        path: String,
        content: InputStream,
        contentType: String
    )

    fun get(path: String, os: OutputStream)
}
