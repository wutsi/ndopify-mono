package com.wutsi.ndopify.agent.dto

import jakarta.validation.constraints.NotEmpty

data class UpdateImageRequest(
    @get:NotEmpty val url: String = "",
)
