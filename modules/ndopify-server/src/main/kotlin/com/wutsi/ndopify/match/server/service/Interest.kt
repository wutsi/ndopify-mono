package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.match.server.domain.MatchEntity

/**
 * The match of a (mandate, listing) pair a customer asked for on a listing's public page, and whether it was created
 * ([created] = false: the pair already had an ACTIVE match, reused).
 */
data class Interest(
    val match: MatchEntity,
    val created: Boolean,
)
