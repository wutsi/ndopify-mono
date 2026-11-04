package com.wutsi.ndopify.visit.dto

import com.wutsi.ndopify.match.dto.MatchRejectionReason
import java.util.Date

data class Visit(
    val id: Long = -1,
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

    // Only returned to the visit's customer, from CONFIRMED: the agent asks for it at check-in
    val code: String? = null,

    // The exact address of the property: only to the customer of an ON_SITE visit, from CONFIRMED
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,

    val checkedInAt: Date? = null,
    val checkInDistanceMeters: Int? = null,
    val checkInFlagged: Boolean = false,

    val checkedOutAt: Date? = null,
    val agentComment: String? = null, // Private to the agent and the admins

    val interest: VisitInterest? = null,
    val feedbackReasonCode: MatchRejectionReason? = null,
    val feedbackComment: String? = null,

    val noShowParty: VisitParty? = null,
    val noShowReason: VisitNoShowReason? = null,

    val cancelledBy: VisitParty? = null,
    val cancellationReason: String? = null,
    val cancelledAt: Date? = null,

    val createdAt: Date = Date(),
    val version: Long? = null,
)
