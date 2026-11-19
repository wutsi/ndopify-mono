package com.wutsi.ndopify.match.server.event

/** A customer's interest recorded as ACCEPTED (new or reopened): its booking-link e-mail follows after commit. */
data class MatchInterestAcceptedEvent(val matchId: Long, val tenantId: Long)
