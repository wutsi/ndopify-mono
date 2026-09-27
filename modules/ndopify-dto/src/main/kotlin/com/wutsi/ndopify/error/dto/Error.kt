package com.wutsi.ndopify.error.dto

data class Error(
    val code: String = "",
    val message: String? = null,
    val parameter: Parameter? = null,
    val traceId: String? = null,
    val downstreamCode: String? = null,
    val downstreamMessage: String? = null,
    val data: Map<String, Any>? = null,
)
