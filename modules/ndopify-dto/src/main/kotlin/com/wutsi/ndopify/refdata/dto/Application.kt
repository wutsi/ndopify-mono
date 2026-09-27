package com.wutsi.ndopify.refdata.dto

data class Application(
    val id: Long? = null,
    val code: String = "",
    val roles: List<Role> = emptyList(),
    val supportedAuthTypes: List<AuthType> = emptyList(),
)
