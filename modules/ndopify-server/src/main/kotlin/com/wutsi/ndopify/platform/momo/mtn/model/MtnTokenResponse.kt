package com.wutsi.ndopify.platform.momo.mtn.model

import com.fasterxml.jackson.annotation.JsonProperty

data class MtnTokenResponse(
    @get:JsonProperty("access_token") val accessToken: String = "",
    @get:JsonProperty("token_type") val tokenType: String = "",
    @get:JsonProperty("expires_in") val expiresIn: Int = -1,
)
