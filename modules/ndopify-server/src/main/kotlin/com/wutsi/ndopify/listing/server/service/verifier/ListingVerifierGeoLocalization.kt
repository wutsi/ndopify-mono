package com.wutsi.ndopify.listing.server.service.verifier

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.service.verifier.ListingVerifierGeoLocalization.Companion.MAX_DISTANCE_KM
import com.wutsi.ndopify.listing.server.service.verifier.ListingVerifierGeoLocalization.Companion.SLACK_KM
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.server.service.LocationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Make sure that the listing is located in the neighborhood it claims to be.
 *
 * The neighborhoods only have a center, no boundaries: a point belongs to the neighborhood whose center is the
 * nearest. The listing is accepted when the claimed neighborhood is no more than [SLACK_KM] farther than the nearest
 * neighborhood of the city (the centers are approximate, and a listing can be at the border of two), and no more
 * than [MAX_DISTANCE_KM] away. Unlike a fixed radius, this adapts to the density of the neighborhoods: strict
 * downtown, lenient in the outskirts.
 *
 * Rejection error codes, in the order they are checked:
 * - [ErrorCode.LISTING_MISSING_LAT_LONG]: the listing has no coordinates
 * - [ErrorCode.LISTING_MISSING_NEIGHBORHOOD]: the listing has no neighborhood
 * - [ErrorCode.LISTING_INVALID_NEIGHBORHOOD]: the neighborhood doesn't exist, or isn't a neighborhood of the city
 *   of the listing
 * - [ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD]: the coordinates are not in the neighborhood
 *
 * A neighborhood without coordinates can't be checked: the listing is accepted.
 */
@Service
class ListingVerifierGeoLocalization(
    private val locationService: LocationService,
) : ListingVerifier {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingVerifierGeoLocalization::class.java)
        const val SLACK_KM = 1.0
        const val MAX_DISTANCE_KM = 5.0
        private const val EARTH_RADIUS_KM = 6371.0
    }

    override fun verify(listing: ListingEntity): ListingVerification {
        val lat = listing.latitude
        val long = listing.longitude
        if (lat == null || long == null) {
            return reject(ErrorCode.LISTING_MISSING_LAT_LONG)
        }

        // Validate the neighborhood: it must exist, be a neighborhood, and belong to the city of the listing
        val neighborhoodId = listing.neighborhoodId
            ?: return reject(ErrorCode.LISTING_MISSING_NEIGHBORHOOD)
        val neighborhood = locationService.findByIdOrNull(neighborhoodId)
        if (neighborhood == null || neighborhood.type != LocationType.NEIGHBORHOOD || neighborhood.parentId != listing.cityId) {
            return reject(ErrorCode.LISTING_INVALID_NEIGHBORHOOD)
        }

        val neighborhoodLat = neighborhood.latitude
        val neighborhoodLong = neighborhood.longitude
        if (neighborhoodLat == null || neighborhoodLong == null) {
            LOGGER.warn(">>> Listing#${listing.id} - Neighborhood#$neighborhoodId has no coordinates, unable to verify the location")
            return ListingVerification(success = true)
        }

        val distance = calculateDistanceKm(lat, long, neighborhoodLat, neighborhoodLong)
        // Never null: the claimed neighborhood has coordinates and is in the city
        val nearest = locationService.findNearest(lat, long, LocationType.NEIGHBORHOOD, listing.cityId)
        val nearestDistance = nearest?.second ?: distance
        LOGGER.info(
            ">>> Listing#${listing.id} - ($lat, $long) is $distance km from ${neighborhood.name}, " +
                "the nearest neighborhood is ${nearest?.first?.name} at $nearestDistance km"
        )

        if (distance > MAX_DISTANCE_KM || distance > nearestDistance + SLACK_KM) {
            return reject(ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD)
        }
        return ListingVerification(success = true)
    }

    private fun reject(error: String): ListingVerification {
        return ListingVerification(
            success = false,
            errorCode = error
        )
    }

    /**
     * Computes the distance in kilometers between two latitude/longitude points
     * using the Haversine formula.
     */
    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
