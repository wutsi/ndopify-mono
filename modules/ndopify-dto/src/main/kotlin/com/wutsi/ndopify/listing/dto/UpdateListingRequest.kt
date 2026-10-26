package com.wutsi.ndopify.listing.dto

import java.time.LocalDate

/**
 * Saves one [section] of the listing: only that section's fields are changed (the others are ignored, and may be
 * left out), as sent, so a null clears an optional field. The version is required.
 */
data class UpdateListingRequest(
    // The listing's version the caller read (Listing.version): the update fails (409) if it changed since
    val version: Long = -1,

    val section: ListingSection = ListingSection.UNKNOWN,

    val propertyCategory: PropertyCategory? = null,
    val propertyType: PropertyType? = null,

    val neighborhoodId: Long? = null,
    val landmark: String? = null,
    val accessDirections: String? = null,

    val longitude: Double? = null,
    val latitude: Double? = null,

    val furnitureType: FurnitureType? = null,
    val rooms: Int? = null,
    val bathrooms: Int? = null,
    val livingRooms: Int? = null,
    val kitchens: Int? = null,
    val parkings: Int? = null,
    val area: Int? = null,
    val levels: Int? = null,
    val floorLevel: Int? = null,

    val waterMeter: Boolean? = null,
    val dedicatedWaterMeter: Boolean? = null,
    val waterTank: Boolean? = null,
    val waterWell: Boolean? = null,

    val electricityMeter: Boolean? = null,
    val dedicatedElectricityMeter: Boolean? = null,
    val generatorSet: Boolean? = null,
    val solarPanel: Boolean? = null,

    val roadPaved: Boolean? = null,
    val trackAccessibleByCar: Boolean? = null,
    val trackInaccessibleByCar: Boolean? = null,

    val securedFenced: Boolean? = null,
    val securityGuard: Boolean? = null,
    val videoSurveillance: Boolean? = null,

    val price: Long? = null,
    val securityDeposit: Int? = null,
    val advanceRent: Int? = null,
    val leaseTerm: Int? = null,
    val noticePeriod: Int? = null,

    val availableFrom: LocalDate? = null,

    val agentRemarks: String? = null,
)
