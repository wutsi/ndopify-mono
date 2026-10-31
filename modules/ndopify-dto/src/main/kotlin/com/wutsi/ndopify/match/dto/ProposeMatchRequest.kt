package com.wutsi.ndopify.match.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class ProposeMatchRequest(
    // The matches of one search mandate, proposed to its customer in one e-mail
    @get:NotEmpty @get:Size(max = 20) val matchIds: List<Long> = emptyList(),
)
