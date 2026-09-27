package com.wutsi.ndopify.platform.momo

import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchRequest
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchResponse

interface MoMoGateway {
    @Throws(MoMoException::class)
    fun kycMatch(request: MoMoKycMatchRequest): MoMoKycMatchResponse
}
