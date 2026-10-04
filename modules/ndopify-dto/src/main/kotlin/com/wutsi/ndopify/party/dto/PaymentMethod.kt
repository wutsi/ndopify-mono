package com.wutsi.ndopify.party.domain

import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.refdata.dto.VerificationStatus
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.Date

@Entity
@Table(name = "T_PAYMENT_METHOD")
data class PaymentMethodEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "party_id")
    val party: PartyEntity = PartyEntity(),

    val number: String = "",
    val providerName: String? = null,
    val holderName: String? = null,
    val methodType: PaymentMethodType = PaymentMethodType.UNKNOWN,
    val verificationStatus: VerificationStatus = VerificationStatus.UNKNOWN,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
