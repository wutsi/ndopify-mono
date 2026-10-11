package com.wutsi.ndopify.npc.ui.join

/** Step "identite". Phone numbers are E.164, filled in by intl-tel-input on the page. */
data class IdentityForm(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val whatsappNumber: String = "",
)
