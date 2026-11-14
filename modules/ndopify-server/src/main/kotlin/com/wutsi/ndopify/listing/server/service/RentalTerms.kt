package com.wutsi.ndopify.listing.server.service

/**
 * The caution and advance bounds the API accepts, like the BFF's `RentalTerms`: null (none), or a legacy 0 to 24.
 */
object RentalTerms {
    const val MAX_MONTHS = 24

    fun isValid(value: Int?): Boolean = value == null || value in 0..MAX_MONTHS
}
