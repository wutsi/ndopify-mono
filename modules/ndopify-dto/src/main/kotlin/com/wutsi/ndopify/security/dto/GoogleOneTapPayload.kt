package com.wutsi.ndopify.security.dto

data class GoogleOneTapPayload(
    val sub: String = "",
    val givenName: String = "",
    val familyName: String = "",
    val picture: String = "",
    val email: String = ""
)
