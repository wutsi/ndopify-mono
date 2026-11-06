package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Rules out or scores one (search mandate, listing) pair. Pure: what the pair needs from elsewhere (distances, today)
 * is passed in. See `docs/superpowers/specs/2026-11-06-match-property-type-score-design.md` for the property type
 * matrix, and `docs/superpowers/plans/2026-10-31-search-listing-matching.md` for the weights and the other rules.
 *
 * Ruled out (`null`), in this order:
 * 1. the listing is the search customer's own property;
 * 2. other `listingType`, `cityId`, or `propertyCategory` (a `BUILDING` pair is decided by its type instead);
 * 3. an incompatible `propertyType` (`typeScore` is 0.0): `LAND` against non-land, a residential cross-type, an
 *    unknown listed type;
 * 4. with a budget: other `currencyCode` or `pricePeriodicityType`;
 * 5. with a budget: a price over the budget + `priceTolerance`.
 *
 * The score is the weighted sum of its partials multiplied by `typeScore`: 1.0 for the searched type (or none),
 * 0.8-0.9 for a commercial sibling.
 */
@Service
class Matcher(
    @param:Value("\${ndopify.match.price-tolerance}") private val priceTolerance: Double,
    @param:Value("\${ndopify.match.nearby-radius-km}") private val nearbyRadiusKm: Double,
) {
    companion object {
        const val WEIGHT_NEIGHBORHOOD = 0.30
        const val WEIGHT_PRICE = 0.25
        const val WEIGHT_ROOMS = 0.15
        const val WEIGHT_AMENITIES = 0.15
        const val WEIGHT_AVAILABILITY = 0.10
        const val WEIGHT_FURNITURE = 0.05

        // Commercial types a BUILDING or an INDUSTRIAL one can stand in for
        private val OFFICE_OR_RETAIL = setOf(PropertyType.OFFICE, PropertyType.RETAIL)
        private val INDUSTRIAL_SIBLINGS =
            setOf(PropertyType.OFFICE, PropertyType.RETAIL, PropertyType.BUILDING)
    }

    /**
     * Returns the score of the pair, or `null` when it's ruled out. [distances]: neighbourhood id → km to the nearest
     * of the searched ones, the searched ones at 0. [today]: the tenant's local date.
     */
    fun score(
        search: MandateEntity,
        listing: ListingEntity,
        distances: Map<Long, Double>,
        today: LocalDate,
    ): MatchScore? {
        val type = typeScore(search.propertyType, listing.propertyType)
        if (isRuledOut(search, listing) || type == 0.0) {
            return null
        }
        return MatchScore(
            neighborhood = neighborhood(search, listing, distances),
            price = price(search, listing),
            rooms = rooms(search, listing),
            amenities = amenities(search, listing),
            availability = availability(search, listing, today),
            furniture = furniture(search, listing),
            type = type,
            distanceKm = distanceKm(search, listing, distances),
            missingAmenities = requested(search, listing).filterNot { (_, has) -> has }.map { (key, _) -> key },
        )
    }

    private fun isRuledOut(search: MandateEntity, listing: ListingEntity): Boolean =
        listing.mandate.customerId == search.customerId ||
            listing.listingType != search.listingType ||
            listing.cityId != search.cityId ||
            categoryMismatch(search, listing) ||
            (
                search.price > 0 &&
                    (
                        listing.currencyCode != search.currencyCode ||
                            listing.pricePeriodicityType != search.pricePeriodicityType ||
                            listing.price > search.price * (1.0 + priceTolerance)
                        )
                )

    // The type is the specific fact; the category is derived, and the two wizards map BUILDING differently
    private fun categoryMismatch(search: MandateEntity, listing: ListingEntity): Boolean =
        listing.propertyCategory != search.propertyCategory &&
            search.propertyType != PropertyType.BUILDING &&
            listing.propertyType != PropertyType.BUILDING

    // 1.0 for the searched type (or none), 0.8-0.9 for a commercial sibling, 0.0 when incompatible
    private fun typeScore(search: PropertyType?, listing: PropertyType?): Double {
        if (search == null || search == PropertyType.UNKNOWN) {
            return 1.0
        }
        if (listing == null || listing == PropertyType.UNKNOWN) {
            return 0.0
        }
        if (search == listing) {
            return 1.0
        }
        if (search == PropertyType.LAND || listing == PropertyType.LAND) {
            return 0.0
        }
        return when {
            search == PropertyType.BUILDING && listing in OFFICE_OR_RETAIL -> 0.85
            listing == PropertyType.BUILDING && search in OFFICE_OR_RETAIL -> 0.85
            search == PropertyType.OFFICE && listing == PropertyType.RETAIL -> 0.9
            search == PropertyType.RETAIL && listing == PropertyType.OFFICE -> 0.9
            search == PropertyType.INDUSTRIAL && listing in INDUSTRIAL_SIBLINGS -> 0.8
            listing == PropertyType.INDUSTRIAL && search in INDUSTRIAL_SIBLINGS -> 0.8
            else -> 0.0
        }
    }

    // In a searched neighbourhood (or none searched): 1. Nearby: 0..0.5 by distance
    private fun neighborhood(search: MandateEntity, listing: ListingEntity, distances: Map<Long, Double>): Double {
        if (search.neighborhoodIds.isEmpty() || listing.neighborhoodId in search.neighborhoodIds) {
            return 1.0
        }
        val distance = listing.neighborhoodId?.let { distances[it] } ?: return 0.0
        return (0.5 * (1.0 - distance / nearbyRadiusKm)).coerceIn(0.0, 0.5)
    }

    // Within the budget (or none): 1. Over it: down to 0 at budget + priceTolerance
    private fun price(search: MandateEntity, listing: ListingEntity): Double {
        if (search.price <= 0 || listing.price <= search.price) {
            return 1.0
        }
        val over = (listing.price - search.price).toDouble()
        return (1.0 - over / (search.price * priceTolerance)).coerceIn(0.0, 1.0)
    }

    // "3": exactly 3 → 1, 4 → 0.7, 2 → 0.3. "5+": 5 or more → 1, 4 → 0.3
    private fun rooms(search: MandateEntity, listing: ListingEntity): Double {
        val value = search.rooms?.trim()?.ifEmpty { null } ?: return 1.0
        val orMore = value.endsWith("+")
        val wanted = value.removeSuffix("+").trim().toIntOrNull() ?: return 1.0
        val rooms = listing.rooms ?: return 0.0
        return when {
            rooms == wanted || (orMore && rooms > wanted) -> 1.0
            rooms == wanted + 1 -> 0.7
            rooms == wanted - 1 -> 0.3
            else -> 0.0
        }
    }

    // Null when it isn't in a searched or nearby neighbourhood, or none is searched
    private fun distanceKm(search: MandateEntity, listing: ListingEntity, distances: Map<Long, Double>): Double? {
        if (search.neighborhoodIds.isEmpty()) {
            return null
        }
        return listing.neighborhoodId?.let { distances[it] }
    }

    // Share of the amenities requested that the listing has
    private fun amenities(search: MandateEntity, listing: ListingEntity): Double {
        val requested = requested(search, listing)
        if (requested.isEmpty()) {
            return 1.0
        }
        return requested.count { (_, has) -> has }.toDouble() / requested.size
    }

    // The amenities requested (by DTO field name), each with whether the listing has it. trackInaccessibleByCar isn't
    // an amenity
    private fun requested(search: MandateEntity, listing: ListingEntity): List<Pair<String, Boolean>> = listOf(
        Triple("waterMeter", search.waterMeter, listing.waterMeter),
        Triple("dedicatedWaterMeter", search.dedicatedWaterMeter, listing.dedicatedWaterMeter),
        Triple("waterTank", search.waterTank, listing.waterTank),
        Triple("waterWell", search.waterWell, listing.waterWell),
        Triple("electricityMeter", search.electricityMeter, listing.electricityMeter),
        Triple("dedicatedElectricityMeter", search.dedicatedElectricityMeter, listing.dedicatedElectricityMeter),
        Triple("generatorSet", search.generatorSet, listing.generatorSet),
        Triple("solarPanel", search.solarPanel, listing.solarPanel),
        Triple("roadPaved", search.roadPaved, listing.roadPaved),
        Triple("trackAccessibleByCar", search.trackAccessibleByCar, listing.trackAccessibleByCar),
        Triple("securedFenced", search.securedFenced, listing.securedFenced),
        Triple("securityGuard", search.securityGuard, listing.securityGuard),
        Triple("videoSurveillance", search.videoSurveillance, listing.videoSurveillance),
    ).filter { (_, wanted, _) -> wanted }.map { (key, _, has) -> key to has }

    // Available by the moving deadline (or none): 1. Up to a month after it: 0.5
    private fun availability(search: MandateEntity, listing: ListingEntity, today: LocalDate): Double {
        val deadline = when (search.movingPeriod) {
            MovingPeriod.WITHIN_1_MONTH -> today.plusMonths(1)
            MovingPeriod.WITHIN_3_MONTHS -> today.plusMonths(3)
            else -> return 1.0
        }
        val availableFrom = listing.availableFrom ?: return 1.0
        return when {
            !availableFrom.isAfter(deadline) -> 1.0
            !availableFrom.isAfter(deadline.plusMonths(1)) -> 0.5
            else -> 0.0
        }
    }

    private fun furniture(search: MandateEntity, listing: ListingEntity): Double =
        if (search.furnitureType == null ||
            search.furnitureType == FurnitureType.UNKNOWN ||
            search.furnitureType == listing.furnitureType
        ) {
            1.0
        } else {
            0.0
        }
}
