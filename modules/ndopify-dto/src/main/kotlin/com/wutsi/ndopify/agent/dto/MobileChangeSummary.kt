package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.refdata.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import java.util.Date

data class MobileChangeSummary(
    val id: Long = -1,
    val agentId: Long = -1,
    val oldMobileNumber: String? = null,
    val newMobileNumber: String = "",
    val newGateway: MoMoGatewayType = MoMoGatewayType.UNKNOWN,
    var status: KycStatus = KycStatus.PENDING,
    val createdAt: Date = Date(),
    val verifiedAt: Date = Date(),
)
