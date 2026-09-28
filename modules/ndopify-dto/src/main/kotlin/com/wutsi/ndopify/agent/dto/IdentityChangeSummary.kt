package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.refdata.dto.IdentityType
import com.wutsi.ndopify.refdata.dto.KycStatus
import java.util.Date

data class IdentityChangeSummary(
    val id: Long? = null,
    val agentId: Long = -1,
    val oldFirstName: String = "",
    val oldLastName: String = "",
    val newFirstName: String = "",
    val newLastName: String = "",
    val identityType: IdentityType = IdentityType.UNKNOWN,
    var status: KycStatus = KycStatus.PENDING,
    val createdAt: Date = Date(),
    var verifiedAt: Date? = null,
)
