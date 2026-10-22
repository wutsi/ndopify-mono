package com.wutsi.ndopify.npc.ui.offerproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.PropertyCategory

/** Step "property". */
data class PropertyForm(
    val propertyCategory: PropertyCategory? = null,
)

/** Step "address". */
data class AddressForm(
    val cityId: Long? = null,
    val neighborhoodId: Long? = null,
)

/** Step "terms". Amounts and the date are text: they may come with separators ("150 000"), and are parsed by the controller. */
data class TermsForm(
    val priceAmount: String? = null,
    val advanceRent: String? = null,
    val securityDeposit: String? = null,
    val availableFrom: String? = null,
)

/** Step "features". `hasParking` is `YES` or `NO`; `parkings` (the places) only counts with `YES`. */
data class FeaturesForm(
    val propertyType: String? = null,
    val furnitureType: String? = null,
    val rooms: String? = null,
    val hasParking: String? = null,
    val parkings: String? = null,
    val area: String? = null,
)

/** Step "amenities": the road access (one of [OfferPropertyRules.ROAD_ACCESS]) and the names of the checked boxes. */
data class AmenitiesForm(
    val roadAccess: String? = null,
    val amenities: List<String> = emptyList(),
)

/** Step "contact". */
data class ContactForm(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val whatsappNumber: String = "",
    val preferredContactPeriod: ContactPeriodType? = null,
)
