package com.wutsi.ndopify.platform.momo.model

data class MoMoKycMatchRequest(
    val phoneNumber: String,
    val holderName: String,
    val countryCode: String,
)
