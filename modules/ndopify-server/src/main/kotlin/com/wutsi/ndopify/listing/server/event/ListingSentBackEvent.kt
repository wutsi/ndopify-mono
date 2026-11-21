package com.wutsi.ndopify.listing.server.event

/**
 * Published by `ListingVerifierService.verify`, inside the transaction that saves a listing back to DRAFT with
 * `errorCodes` (a verifier rejection, or `INTERNAL_ERROR` after `MAX_RETRIES`). Carries ids rather than the entity,
 * so listeners reload what they need. [tenantId] is the listing's tenant, since listeners run on a thread with none.
 */
data class ListingSentBackEvent(
    val listingId: Long,
    val tenantId: Long,
)
