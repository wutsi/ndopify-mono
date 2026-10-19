package com.wutsi.ndopify.esignature.ui.sign

/** With [resend], the e-mail is the session's [PendingSignature]'s, never the posted one. */
data class OtpForm(
    val email: String = "",
    val resend: Boolean = false,
)
