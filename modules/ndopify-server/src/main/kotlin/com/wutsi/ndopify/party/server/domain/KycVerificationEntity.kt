package com.wutsi.ndopify.party.server.domain

import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "T_KYC_VERIFICATION")
data class KycVerificationEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne
    @JoinColumn(name = "case_id")
    val case: KycCaseEntity = KycCaseEntity(),

    val type: KycVerificationType = KycVerificationType.UNKNOWN,
    val status: KycStatus = KycStatus.UNKNOWN,
    val score: Int? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
