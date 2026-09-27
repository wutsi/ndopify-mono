package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.refdata.dto.KycStatus

data class VerifyMobileChangeResponse(
    var status: KycStatus = KycStatus.PENDING,
    var errorCode: String? = null,
    var failureReason: String? = null,
)
