package com.wutsi.ndopify.visit.dto

import java.util.Date

/** A visit as a list shows it: no code, address nor position, no agent's comment nor the customer's feedback. */
data class VisitSummary(
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

    val checkedInAt: Date? = null,
    val checkInDistanceMeters: Int? = null,
    val checkInFlagged: Boolean = false,

    val checkedOutAt: Date? = null,

    val noShowParty: VisitParty? = null,
    val noShowReason: VisitNoShowReason? = null,

    val cancelledBy: VisitParty? = null,
    val cancellationReason: String? = null,
    val cancelledAt: Date? = null,

    val createdAt: Date = Date(),
    val version: Long? = null,
)
