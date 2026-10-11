package com.wutsi.ndopify.npc.ui.login

/** The e-mail isn't part of the form: it comes from the session's [PendingLogin], so it can't be tampered with. */
data class VerifyForm(
    val code: String = "",
)
