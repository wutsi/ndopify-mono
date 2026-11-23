package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.ListingType
import java.io.Serializable

/**
 * A sent request, kept in the HTTP session after `POST /find-property/review` so the confirmation survives a reload.
 * Its presence is what allows the confirmation to be shown. Its project words the confirmation (a rental by default).
 */
data class SubmittedMandate(
    val email: String,
    val whatsappNumber: String,
    val preferredContactPeriod: ContactPeriodType?,
    val listingType: ListingType = ListingType.RENTAL,
) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.submitted-mandate"
    }
}
