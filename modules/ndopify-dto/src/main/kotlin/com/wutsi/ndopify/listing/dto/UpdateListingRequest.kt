package com.wutsi.ndopify.listing.dto

import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.time.LocalDate

data class UpdateListingRequest(
    val propertyCategory: PropertyCategory = PropertyCategory.UNKNOWN,
    val propertyType: PropertyType? = null,

    val cityId: Long = -1,
    val neighborhoodId: Long = -1,
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

    val waterMeter: Boolean = false,
    val dedicatedWaterMeter: Boolean = false,
    val waterTank: Boolean = false,
    val waterWell: Boolean = false,

    val electricityMeter: Boolean = false,
    val dedicatedElectricityMeter: Boolean = false,
    val generatorSet: Boolean = false,
    val solarPanel: Boolean = false,

    val roadPaved: Boolean = false,
    val trackAccessibleByCar: Boolean = false,
    val trackInaccessibleByCar: Boolean = false,

    val securedFenced: Boolean = false,
    val securityGuard: Boolean = false,
    val videoSurveillance: Boolean = false,

    val price: Long = -1,
    val currencyCode: String = "",
    val pricePeriodicityType: PeriodicityType = PeriodicityType.UNKNOWN,
    val securityDeposit: Int? = null,
    val advanceRent: Int? = null,
    val leaseTerm: Int? = null,
    val noticePeriod: Int? = null,

    val availableFrom: LocalDate? = null,

    val agentRemarks: String? = null,

    // The listing's version the caller read (Listing.version): the update fails (409) if it changed since. Not checked if null
    val version: Long? = null,
)
