package com.wutsi.ndopify.web.ui.visit

/** The cancellation form; a blank [reason] is treated as none by [VisitController]. */
data class CancelVisitForm(
    val reason: String? = null,
)
