package com.wutsi.ndopify.platform.util.http

/** A file of a `multipart/form-data` request: the field it's posted as, its filename, its type and its bytes. */
data class HttpFile(
    val fieldName: String,
    val filename: String,
    val contentType: String,
    val content: ByteArray,
)
