package com.wutsi.ndopify.security.dto

import com.wutsi.ndopify.refdata.dto.AuthType
import jakarta.validation.constraints.NotBlank

data class AuthenticateRequest(
    @get:NotBlank
    val email: String = "",

    @get:NotBlank
    val applicationCode: String = "",

    val authType: AuthType = AuthType.UNKNOWN,

    val secret: String? = null,
    val googleOneTapPayload: GoogleOneTapPayload? = null,
)
