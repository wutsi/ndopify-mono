package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.web.core.service.TenantFormatter

/**
 * What a section's form posts (`POST /listings/{id}/{section}`): every section's fields, only those of the posted
 * section read ([ListingRules.request]). Values are Strings so that a hand-edited one is a field error, not a binding
 * failure; [version] is the listing's when the form was shown.
 */
data class ListingSectionForm(
    val version: Long = -1,
    // LOCATION
    val neighborhoodId: String? = null,
    val landmark: String? = null,
    val accessDirections: String? = null,
    // GPS: "4.0335" or "4,0335"
    val latitude: String? = null,
    val longitude: String? = null,
    // PROPERTY
    val propertyCategory: String? = null,
    val propertyType: String? = null,
    val furnitureType: String? = null,
    val rooms: String? = null,
    val bathrooms: String? = null,
    val livingRooms: String? = null,
    val kitchens: String? = null,
    val parkings: String? = null,
    val area: String? = null,
    val levels: String? = null,
    val floorLevel: String? = null,
    // AMENITIES: the road access is one answer, the others are checkboxes
    val waterMeter: Boolean = false,
    val dedicatedWaterMeter: Boolean = false,
    val waterTank: Boolean = false,
    val waterWell: Boolean = false,
    val electricityMeter: Boolean = false,
    val dedicatedElectricityMeter: Boolean = false,
    val generatorSet: Boolean = false,
    val solarPanel: Boolean = false,
    val roadAccess: String? = null,
    val securedFenced: Boolean = false,
    val securityGuard: Boolean = false,
    val videoSurveillance: Boolean = false,
    // TERMS: price as typed ("150 000"), availableFrom as yyyy-MM-dd
    val price: String? = null,
    val securityDeposit: String? = null,
    val advanceRent: String? = null,
    val leaseTerm: String? = null,
    val noticePeriod: String? = null,
    val availableFrom: String? = null,
    // REMARKS
    val agentRemarks: String? = null,
) {
    companion object {
        /** The listing's current values, as its forms show them. */
        fun of(listing: Listing, formatter: TenantFormatter) = ListingSectionForm(
            version = listing.version,
            neighborhoodId = listing.neighborhoodId?.toString(),
            landmark = listing.landmark,
            accessDirections = listing.accessDirections,
            latitude = listing.latitude?.toString(),
            longitude = listing.longitude?.toString(),
            propertyCategory = listing.propertyCategory.takeIf { it != PropertyCategory.UNKNOWN }?.name,
            propertyType = listing.propertyType?.name,
            furnitureType = listing.furnitureType?.name,
            rooms = listing.rooms?.toString(),
            bathrooms = listing.bathrooms?.toString(),
            livingRooms = listing.livingRooms?.toString(),
            kitchens = listing.kitchens?.toString(),
            parkings = listing.parkings?.toString(),
            area = listing.area?.toString(),
            levels = listing.levels?.toString(),
            floorLevel = listing.floorLevel?.toString(),
            waterMeter = listing.waterMeter,
            dedicatedWaterMeter = listing.dedicatedWaterMeter,
            waterTank = listing.waterTank,
            waterWell = listing.waterWell,
            electricityMeter = listing.electricityMeter,
            dedicatedElectricityMeter = listing.dedicatedElectricityMeter,
            generatorSet = listing.generatorSet,
            solarPanel = listing.solarPanel,
            roadAccess = when {
                listing.roadPaved -> ListingRules.ROAD_PAVED
                listing.trackAccessibleByCar -> ListingRules.TRACK_ACCESSIBLE_BY_CAR
                listing.trackInaccessibleByCar -> ListingRules.TRACK_INACCESSIBLE_BY_CAR
                else -> null
            },
            securedFenced = listing.securedFenced,
            securityGuard = listing.securityGuard,
            videoSurveillance = listing.videoSurveillance,
            price = listing.price.takeIf { it > 0 }?.let { formatter.count(it) },
            securityDeposit = listing.securityDeposit?.toString(),
            advanceRent = listing.advanceRent?.toString(),
            leaseTerm = listing.leaseTerm?.toString(),
            noticePeriod = listing.noticePeriod?.toString(),
            availableFrom = listing.availableFrom?.toString(),
            agentRemarks = listing.agentRemarks,
        )
    }

    /** The checked amenities, by their `Listing` names (the road access included). */
    fun amenities(): Set<String> =
        listOfNotNull(
            "waterMeter".takeIf { waterMeter },
            "dedicatedWaterMeter".takeIf { dedicatedWaterMeter },
            "waterTank".takeIf { waterTank },
            "waterWell".takeIf { waterWell },
            "electricityMeter".takeIf { electricityMeter },
            "dedicatedElectricityMeter".takeIf { dedicatedElectricityMeter },
            "generatorSet".takeIf { generatorSet },
            "solarPanel".takeIf { solarPanel },
            roadAccess?.takeIf { it in ListingRules.ROAD_ACCESS },
            "securedFenced".takeIf { securedFenced },
            "securityGuard".takeIf { securityGuard },
            "videoSurveillance".takeIf { videoSurveillance },
        ).toSet()
}
