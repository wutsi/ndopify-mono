package com.wutsi.ndopify.util.http

import java.io.IOException

class HttpException(
    val statusCode: Int,
    val bodyString: String,
    message: String? = null,
    cause: Exception? = null,
) : IOException(message, cause) {
    override val message: String
        get() = "$statusCode - $bodyString" + super.message?.let { "\n$it" }
}
