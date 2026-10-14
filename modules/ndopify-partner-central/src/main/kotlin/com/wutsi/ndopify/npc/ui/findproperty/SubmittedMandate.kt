package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import java.io.Serializable

/**
 * A sent request, kept in the HTTP session after `POST /find-property/review` so the confirmation survives a reload.
 * Its presence is what allows the confirmation to be shown.
 */
data class SubmittedMandate(
    val email: String,
    val whatsappNumber: String,
    val preferredContactPeriod: ContactPeriodType?,
) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.submitted-mandate"
    }
}
