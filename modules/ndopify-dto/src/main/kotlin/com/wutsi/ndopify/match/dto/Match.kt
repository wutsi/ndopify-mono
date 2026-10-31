package com.wutsi.ndopify.match.dto

import java.util.Date

data class Match(
    val id: Long = -1,
    val mandateId: Long = -1,
    val listingId: Long = -1,
    val status: MatchStatus = MatchStatus.UNKNOWN,

    val score: Double = 0.0,
    val neighborhoodScore: Double = 0.0,
    val priceScore: Double = 0.0,
    val roomsScore: Double = 0.0,
    val amenitiesScore: Double = 0.0,
    val availabilityScore: Double = 0.0,
    val furnitureScore: Double = 0.0,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
