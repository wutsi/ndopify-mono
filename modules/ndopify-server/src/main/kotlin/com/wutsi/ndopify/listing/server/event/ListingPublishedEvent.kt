package com.wutsi.ndopify.listing.server.event

/**
 * Published by `ListingPublisherService.publish`, inside its transaction, when a listing becomes PUBLISHED (with or
 * without generated content). Carries ids rather than the entity, so listeners reload what they need. [tenantId] is
 * the listing's tenant, since listeners run on a thread that has none.
 */
data class ListingPublishedEvent(
    val listingId: Long,
    val tenantId: Long,
)
