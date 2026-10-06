package com.wutsi.ndopify.kyc.server.domain

import com.wutsi.ndopify.kyc.dto.KycStatus
import com.wutsi.ndopify.party.server.domain.PartyEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "T_KYC_CASE")
data class KycCaseEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "party_id")
    val party: PartyEntity = PartyEntity(),

    val status: KycStatus = KycStatus.UNKNOWN,
    val failureCode: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
)
