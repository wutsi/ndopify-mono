package com.wutsi.ndopify.platform.momo

import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchRequest
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchResponse

interface MoMoGateway {
    fun getPhoneNumberPrefixes(countryCode: String): List<String>

    @Throws(MoMoException::class)
    fun kycMatch(request: MoMoKycMatchRequest): MoMoKycMatchResponse
}
