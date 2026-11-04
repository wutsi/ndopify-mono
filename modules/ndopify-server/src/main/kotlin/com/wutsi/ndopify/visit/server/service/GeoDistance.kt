package com.wutsi.ndopify.visit.server.service

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The distance between two positions on Earth, by the haversine formula. Used to tell a check-in made on site. */
object GeoDistance {
    private const val EARTH_RADIUS_METERS = 6371000.0

    fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return (2 * EARTH_RADIUS_METERS * asin(sqrt(a))).roundToInt()
    }
}
