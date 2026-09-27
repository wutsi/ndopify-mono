package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.refdata.dto.IdentityType
import jakarta.validation.constraints.NotEmpty

data class UpdateIdentyRequest(
    @get:NotEmpty val firstName: String = "",
    @get:NotEmpty val lastName: String = "",

    val identityType: IdentityType = IdentityType.UNKNOWN,
    @get:NotEmpty val documentPage1Url: String = "",
    val documentPage2Url: String? = null,
)
