package com.wutsi.ndopify.platform.momo.mtn.model

import com.fasterxml.jackson.annotation.JsonProperty

data class MtnBasicUserInfoResponse(
    @get:JsonProperty("given_name") val givenName: String = "",
    @get:JsonProperty("family_name") val familyName: String = "",
    @get:JsonProperty("birth_date") val birthDate: String = "",
    @get:JsonProperty("locale") val locale: String = "",
    @get:JsonProperty("gender") val gender: String = "",
    @get:JsonProperty("status") val status: MtnUserStatus? = MtnUserStatus.ACTIVE,
)
