package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType

/** Step "contact". */
data class ContactForm(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val whatsappNumber: String = "",
    val preferredContactPeriod: ContactPeriodType? = null,
    val comments: String = "",
)
