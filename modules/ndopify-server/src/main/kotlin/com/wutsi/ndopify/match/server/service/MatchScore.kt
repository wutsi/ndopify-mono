package com.wutsi.ndopify.match.server.service

/**
 * The partial scores (each 0..1) of a (search mandate, listing) pair, and their weighted sum.
 */
data class MatchScore(
    val neighborhood: Double,
    val price: Double,
    val rooms: Double,
    val amenities: Double,
    val availability: Double,
    val furniture: Double,
) {
    val total: Double
        get() = Matcher.WEIGHT_NEIGHBORHOOD * neighborhood +
            Matcher.WEIGHT_PRICE * price +
            Matcher.WEIGHT_ROOMS * rooms +
            Matcher.WEIGHT_AMENITIES * amenities +
            Matcher.WEIGHT_AVAILABILITY * availability +
            Matcher.WEIGHT_FURNITURE * furniture
}
