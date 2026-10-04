package com.wutsi.ndopify.party.server.domain

import com.wutsi.ndopify.party.dto.PaymentMethodStatus
import com.wutsi.ndopify.party.dto.PaymentMethodType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "T_PAYMENT_METHOD")
data class PaymentMethodEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "party_id")
    val party: PartyEntity = PartyEntity(),

    val number: String = "",
    val providerName: String? = null,
    val holderName: String? = null,
    val type: PaymentMethodType = PaymentMethodType.UNKNOWN,
    val status: PaymentMethodStatus = PaymentMethodStatus.UNKNOWN,
    val expiresAt: Date? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
