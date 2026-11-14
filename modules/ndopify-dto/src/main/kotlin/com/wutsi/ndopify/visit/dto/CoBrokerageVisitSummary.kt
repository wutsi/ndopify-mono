package com.wutsi.ndopify.visit.dto

import java.util.Date

/**
 * A visit another agent organizes on the listing agent's property. It carries only what the listing agent may see:
 * never the customer, the note, the code, the address, the report or the feedback.
 */
data class CoBrokerageVisitSummary(
    val id: Long = -1,
    val listingId: Long = -1,
    /** A, the search agent; never A's customer. */
    val agentId: Long = -1,
    val status: VisitStatus = VisitStatus.UNKNOWN,
    val type: VisitType = VisitType.UNKNOWN,
    val scheduledAt: Date? = null,
    val checkedInAt: Date? = null,
)
