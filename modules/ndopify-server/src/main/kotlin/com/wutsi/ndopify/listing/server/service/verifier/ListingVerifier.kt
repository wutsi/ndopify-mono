package com.wutsi.ndopify.listing.server.service.verifier

import com.wutsi.ndopify.listing.server.domain.ListingEntity

interface ListingVerifier {
    fun verify(listing: ListingEntity): ListingEntity
}
