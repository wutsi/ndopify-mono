package com.wutsi.ndopify.listing.server.mail

import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity

/** The links to a listing in the e-mails, shared with the other domains' mailets (ex: the match proposal). */
internal object ListingUrls {
    // Its page on the public portal: null without the tenant's publicPortalUrl, or before the listing has a url
    fun publicUrl(tenant: TenantEntity, listing: ListingEntity): String? {
        val baseUrl = tenant.publicPortalUrl ?: return null
        val url = listing.url ?: return null
        return baseUrl.removeSuffix("/") + url
    }
}
