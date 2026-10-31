package com.wutsi.ndopify.match.dto

import jakarta.validation.constraints.Size

data class RejectMatchRequest(
    // Why the customer said no, in its own words
    @get:Size(max = 1000) val reason: String? = null,
)
