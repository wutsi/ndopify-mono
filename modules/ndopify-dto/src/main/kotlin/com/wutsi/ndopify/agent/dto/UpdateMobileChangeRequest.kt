package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.refdata.dto.KycStatus

data class UpdateMobileChangeRequest(
    var holderName: String? = null,
    var status: KycStatus = KycStatus.UNKNOWN,
    var errorCode: String? = null,
    var failureReason: String? = null,
)
