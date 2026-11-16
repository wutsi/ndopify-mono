package com.wutsi.ndopify.mandate.server.event

/**
 * The customer of [mandateId] asked for [listingId] on its public page, and already has a SEARCH mandate of the same city
 * and listing type: the new search would be a duplicate, so the listing is attached to that mandate instead (a match at
 * the customer's request, see `MatchInterestService`). Published in the creation's transaction.
 */
data class ExistingSearchInterestEvent(
    val mandateId: Long,
    val listingId: Long,
    val tenantId: Long,
)
