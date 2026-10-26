package com.wutsi.ndopify.listing.server.domain

import com.wutsi.ndopify.listing.dto.ListingSection
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import java.util.Date

/** A section of a listing the agent saved on site: one row per section, its last save. */
@Entity
@Table(name = "T_LISTING_SECTION")
data class ListingSectionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    val listingId: Long = -1,
    val section: ListingSection = ListingSection.UNKNOWN,
    val verifiedAt: Date = Date(),
)
