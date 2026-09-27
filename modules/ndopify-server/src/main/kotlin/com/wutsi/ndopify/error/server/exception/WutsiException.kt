package com.wutsi.ndopify.error.server.exception

import com.wutsi.ndopify.error.dto.Error

open class WutsiException(val error: Error, cause: Throwable? = null) : RuntimeException(null, cause) {
    override val message: String?
        get() = "error-code=${error.code}"
}
