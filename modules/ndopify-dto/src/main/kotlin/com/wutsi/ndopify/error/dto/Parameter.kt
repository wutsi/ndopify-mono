package com.wutsi.ndopify.error.dto

data class Parameter(
    val name: String? = null,
    val type: ParameterType? = null,
    val value: Any? = null,
)
