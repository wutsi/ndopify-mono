package com.wutsi.ndopify.visit.server.domain

import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.TenantId
import java.util.Date

/**
 * A customer's visit of one of the listings its agent matched to its search: requested, confirmed with a code, checked
 * in and out. The match, mandate and listing ids are kept as they were when the visit was requested, so the story stays
 * readable even if one of them changes afterwards.
 */
@Entity
@Table(name = "T_VISIT")
data class VisitEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    val matchId: Long = -1,
    val mandateId: Long = -1,
    val listingId: Long = -1,
    val agentId: Long = -1,
    val customerId: Long = -1,

    val status: VisitStatus = VisitStatus.UNKNOWN,
    val type: VisitType = VisitType.UNKNOWN,

    val requestedAt: Date = Date(),
    val note: String? = null,
    val scheduledAt: Date? = null,

    // 4 digits, generated on CONFIRMED. Never in a log, nor in a DTO returned to anybody but the customer
    val code: String? = null,
    val codeAttempts: Int = 0,

    val checkedInAt: Date? = null,
    val checkInDistanceMeters: Int? = null,
    val checkInFlagged: Boolean = false,
    val checkInLatitude: Double? = null,
    val checkInLongitude: Double? = null,
    val checkInAccuracy: Double? = null,

    val checkedOutAt: Date? = null,
    val checkOutLatitude: Double? = null,
    val checkOutLongitude: Double? = null,
    val checkOutAccuracy: Double? = null,
    val agentComment: String? = null, // Private to the agent and the admins

    val interest: VisitInterest? = null,
    val feedbackReasonCode: MatchRejectionReason? = null,
    val feedbackComment: String? = null,

    val noShowParty: VisitParty? = null,
    val noShowReason: VisitNoShowReason? = null,
    val noShowLatitude: Double? = null,
    val noShowLongitude: Double? = null,
    val noShowAccuracy: Double? = null,

    val cancelledBy: VisitParty? = null,
    val cancellationReason: String? = null,
    val cancelledAt: Date? = null,

    // Set when the 24h reminder is sent, or when the confirmed e-mail is the reminder (confirmed less than 24h before)
    val reminderSentAt: Date? = null,

    val createdAt: Date = Date(),

    // Optimistic locking: a check-in, a cancel or a job never overwrites a decision made meanwhile
    @Version
    val version: Long? = null,
)
