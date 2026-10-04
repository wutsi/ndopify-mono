package com.wutsi.ndopify.party.dto

import jakarta.validation.constraints.NotEmpty

data class UpdatePhotoRequest(
    @get:NotEmpty val url: String = "",
)
