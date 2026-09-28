package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.refdata.dto.IdentityType
import com.wutsi.ndopify.refdata.dto.KycStatus
import java.util.Date

data class IdentityChange(
    val id: Long? = null,
    var verifyByUserId: Long? = null,
    val agentId: Long = -1,
    val oldFirstName: String = "",
    val oldLastName: String = "",
    val newFirstName: String = "",
    val newLastName: String = "",
    val identityType: IdentityType = IdentityType.UNKNOWN,
    val imageUrls: List<String> = emptyList(),
    var holderName: String? = null,
    val holderNameScore: Double? = null,
    val countryCodeScore: Double? = null,
    val documentTypeScore: Double? = null,
    var retries: Int? = null,
    var status: KycStatus = KycStatus.PENDING,
    var errorCode: String? = null,
    var failureReason: String? = null,
    val createdAt: Date = Date(),
    var verifiedAt: Date? = null,
)
