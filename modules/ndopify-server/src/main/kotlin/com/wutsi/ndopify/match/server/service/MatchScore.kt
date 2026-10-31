package com.wutsi.ndopify.match.server.service

/**
 * The partial scores (each 0..1) of a (search mandate, listing) pair, and their weighted sum. With what explains them
 * to the agent: [distanceKm] from the listing's neighbourhood to the nearest searched one (0 inside one, null when not
 * nearby or none searched), and the [missingAmenities] searched that the listing lacks, by DTO field name.
 */
data class MatchScore(
    val neighborhood: Double,
    val price: Double,
    val rooms: Double,
    val amenities: Double,
    val availability: Double,
    val furniture: Double,
    val distanceKm: Double? = null,
    val missingAmenities: List<String> = emptyList(),
) {
    val total: Double
        get() = Matcher.WEIGHT_NEIGHBORHOOD * neighborhood +
            Matcher.WEIGHT_PRICE * price +
            Matcher.WEIGHT_ROOMS * rooms +
            Matcher.WEIGHT_AMENITIES * amenities +
            Matcher.WEIGHT_AVAILABILITY * availability +
            Matcher.WEIGHT_FURNITURE * furniture
}
