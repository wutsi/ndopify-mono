package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.refdata.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import java.util.Date

data class MobileChange(
    val id: Long = -1,
    val agentId: Long = -1,
    val verifyByUserId: Long? = null,

    val oldMobileNumber: String? = null,
    val newMobileNumber: String = "",
    val newGateway: MoMoGatewayType = MoMoGatewayType.UNKNOWN,

    var holderName: String? = null,
    var status: KycStatus = KycStatus.PENDING,
    var errorCode: String? = null,
    var failureReason: String? = null,
    var retries: Int? = null,

    val createdAt: Date = Date(),
    val verifiedAt: Date? = null,
)
