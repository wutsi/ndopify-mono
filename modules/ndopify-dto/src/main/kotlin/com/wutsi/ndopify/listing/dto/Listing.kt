package com.wutsi.ndopify.listing.dto

import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.time.LocalDate
import java.util.Date

data class Listing(
    val id: Long = -1,
    val version: Long = 0,

    val mandateId: Long = -1,
    val agentId: Long = -1,

    val propertyCategory: PropertyCategory = PropertyCategory.UNKNOWN,
    val listingType: ListingType = ListingType.UNKNOWN,
    val status: ListingStatus = ListingStatus.UNKNOWN,

    val cityId: Long = -1,
    val neighborhoodId: Long? = null,
    val landmark: String? = null,
    val accessDirections: String? = null,

    val longitude: Double? = null,
    val latitude: Double? = null,

    val propertyType: PropertyType? = null,
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

    // The checklist, in ListingSection's order
    val sections: List<ListingSectionSummary> = emptyList(),

    val title: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val errors: List<String> = emptyList(),

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
    val publishedAt: Date? = null,
    val closedAt: Date? = null,
)
