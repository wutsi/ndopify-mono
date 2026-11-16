package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.mandate.dto.CreateMandateRequest

/**
 * The listing a mandate is seeded from: its id, its agent, whether it's still on the market, and the search criteria to
 * copy from it (see [OriginListingResolver]).
 */
data class OriginListing(
    val id: Long,
    val agentId: Long?,
    val published: Boolean,
    val criteria: CreateMandateRequest,
)

/**
 * Turns a listing into the criteria a SEARCH mandate takes from it. Declared by the mandate domain and implemented by
 * the listing domain ([com.wutsi.ndopify.listing.server.service.ListingOriginListingResolver]), so that the mandate
 * never depends on the listing one: the server seeds a mandate from its origin listing through this interface only.
 */
interface OriginListingResolver {
    /** The listing [listingId], or null when it doesn't exist (or belongs to another tenant). */
    fun resolve(listingId: Long): OriginListing?
}
