package com.wutsi.ndopify.listing.server.domain

import com.wutsi.ndopify.listing.dto.ImageStatus
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import java.util.Date

@Entity
@Table(name = "T_LISTING_IMAGE")
data class ListingImageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    val listingId: Long = -1,
    val path: String = "",
    val url: String = "",
    val mimeType: String? = null,
    val status: ImageStatus = ImageStatus.UNKNOWN,
    val errorMessage: String? = null,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
