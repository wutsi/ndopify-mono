package com.wutsi.ndopify.visit.dto

import jakarta.validation.constraints.Size

data class CancelVisitRequest(
    @get:Size(max = 500) val reason: String? = null,
)
