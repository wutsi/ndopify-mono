package com.wutsi.ndopify.party.server.domain

import com.wutsi.ndopify.party.dto.KycStatus
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "identification_id")
    val identification: IdentificationEntity = IdentificationEntity(),

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_method_id")
    val paymentMethod: PaymentMethodEntity? = null,

    @OneToMany(mappedBy = "case")
    val verifications: List<KycVerificationEntity> = emptyList(),

    val score: Int? = null,
    val status: KycStatus = KycStatus.UNKNOWN,
    val errorCode: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
