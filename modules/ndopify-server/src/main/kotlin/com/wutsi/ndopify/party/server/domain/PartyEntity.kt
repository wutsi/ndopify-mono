package com.wutsi.ndopify.party.server.domain

import com.wutsi.ndopify.party.dto.KycStatus
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import java.util.Date

@Entity
@Table(name = "T_PARTY")
data class PartyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — setting any non-null value here makes Hibernate treat it as caller-assigned and throw
    // if it doesn't match the resolved tenant.
    @TenantId
    val tenantId: Long? = null,

    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val kycStatus: KycStatus = KycStatus.UNKNOWN,
    val photoUrl: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
) {
    fun fullName(): String {
        return "$firstName $lastName".trim()
    }
}
