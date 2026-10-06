package com.wutsi.ndopify.party.server.domain

import com.wutsi.ndopify.party.dto.KycStatus
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.Date

@Entity
@Table(name = "T_PARTY")
data class PartyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val kycStatus: KycStatus = KycStatus.UNKNOWN,
    val photoUrl: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
