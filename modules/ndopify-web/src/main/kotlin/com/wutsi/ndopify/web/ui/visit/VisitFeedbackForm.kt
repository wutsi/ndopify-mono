package com.wutsi.ndopify.web.ui.visit

/**
 * The feedback form of a COMPLETED visit: [interest] (`INTERESTED` / `NOT_INTERESTED`), the [reasonCode] when the
 * property doesn't interest the customer, and an optional [comment]. Read as strings so an unknown value is a redirect,
 * never a binding error.
 */
data class VisitFeedbackForm(
    val interest: String? = null,
    val reasonCode: String? = null,
    val comment: String? = null,
)
