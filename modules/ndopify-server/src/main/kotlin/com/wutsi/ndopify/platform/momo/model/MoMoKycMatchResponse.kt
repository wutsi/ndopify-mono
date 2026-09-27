package com.wutsi.ndopify.platform.momo.model

data class MoMoKycMatchResponse(
    val holderNameScore: Double = 0.0,
    val countryCodeScore: Double = 0.0,
    val active: Boolean = false,
    val holderName: String = ""
)
