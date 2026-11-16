package com.wutsi.ndopify.web.ui.listing

import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateType

/**
 * The "Visiter ce logement" form's fields: the visitor's contact, its moving period, and the honeypot. Everything else
 * of the search mandate — city, type, criteria, budget — comes from the listing, which the server reads itself: the
 * form can't be turned into an arbitrary mandate.
 */
data class ListingInterestForm(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val whatsappNumber: String? = null,
    val movingPeriod: String? = null,

    /** The honeypot: a link-like field a person never sees, a bot fills. Never posted to the server. */
    val website: String? = null,
) {
    fun isHoneypotFilled(): Boolean = !website.isNullOrBlank()

    /** The mandate to create: the contact and the moving period only, seeded from the listing [listingId]. */
    fun toRequest(listingId: Long): CreateMandateRequest = CreateMandateRequest(
        mandateType = MandateType.SEARCH,
        originListingId = listingId,
        firstName = firstName?.trim().orEmpty(),
        lastName = lastName?.trim().orEmpty(),
        email = email?.trim().orEmpty(),
        whatsappNumber = whatsappNumber?.trim().orEmpty(),
        movingPeriod = MovingPeriod.entries.firstOrNull { it.name == movingPeriod } ?: MovingPeriod.UNKNOWN,
    )
}
