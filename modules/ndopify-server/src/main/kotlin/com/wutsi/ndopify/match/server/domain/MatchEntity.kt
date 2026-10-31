package com.wutsi.ndopify.match.server.domain

import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.TenantId
import java.util.Date

/** A listing of the agent's inventory matching one of its SEARCH mandates: one per (mandate, listing). */
@Entity
@Table(name = "T_MATCH")
data class MatchEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    @ManyToOne
    @JoinColumn(name = "mandate_id")
    val mandate: MandateEntity = MandateEntity(),

    @ManyToOne
    @JoinColumn(name = "listing_id")
    val listing: ListingEntity = ListingEntity(),

    val agentId: Long = -1L,
    val status: MatchStatus = MatchStatus.UNKNOWN,

    val score: Double = 0.0,
    val neighborhoodScore: Double = 0.0,
    val priceScore: Double = 0.0,
    val roomsScore: Double = 0.0,
    val amenitiesScore: Double = 0.0,
    val availabilityScore: Double = 0.0,
    val furnitureScore: Double = 0.0,

    // The listing's price when last scored, or when dismissed: a later price below it is a price drop, the reason to
    // propose again a match turned down
    val listingPrice: Long = -1,

    // When the agent was e-mailed about it (MatchJobs.notifyAgents): null until then, kept by a re-score
    val notifiedAt: Date? = null,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),

    // Optimistic locking: a re-score never overwrites a dismiss made meanwhile, nor the other way round
    @Version
    val version: Long? = null,
)
