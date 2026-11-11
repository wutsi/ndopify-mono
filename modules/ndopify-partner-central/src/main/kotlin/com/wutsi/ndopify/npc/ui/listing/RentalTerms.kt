package com.wutsi.ndopify.npc.ui.listing

/**
 * The months a caution or an advance may span, shared by the *Proposer un bien* mandate wizard and the listing's
 * *Loyer et conditions*. Both are optional: "none" is null (0 is a legacy value, read as none), not a month.
 */
object RentalTerms {
    const val MIN_MONTHS = 1
    const val MAX_MONTHS = 24

    /** The months a form offers, one by one. */
    val MONTHS: IntRange = MIN_MONTHS..MAX_MONTHS
}
