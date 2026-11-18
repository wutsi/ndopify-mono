package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.match.server.domain.MatchEntity

/**
 * The match of a (mandate, listing) pair a customer asked for on a listing's public page, and whether the row moved to
 * ACCEPTED now ([changed] = false: it was already ACCEPTED, left as is). What moved is created or reopened; the caller
 * notifies the agent and e-mails the customer only once.
 */
data class Interest(
    val match: MatchEntity,
    val changed: Boolean,
)
