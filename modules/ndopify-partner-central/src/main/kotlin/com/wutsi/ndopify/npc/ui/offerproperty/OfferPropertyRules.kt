package com.wutsi.ndopify.npc.ui.offerproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.npc.ui.listing.RentalTerms
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Field rules of the "Proposer un bien" wizard, shared by its controller (to validate what a step posts) and
 * [PendingListing] (to tell which steps are complete). Each returns the French error message, or null when valid.
 * Messages and thresholds are the ones of the `offer-property.js` prototype. ndopify-server re-checks the caution and
 * advance range only, so the other rules here are the only validation a listing gets.
 *
 * The wizard serves two projects: a rental ([ListingType.RENTAL]) and a sale ([ListingType.SALE]). A sale offers land
 * for both uses and no room, asks no furniture, requires the surface of a plot of land and its optional documents, has
 * its own price floor, and has no advance nor deposit.
 */
object OfferPropertyRules {
    const val STEP_PROPERTY = "property"
    const val STEP_ADDRESS = "address"
    const val STEP_TERMS = "terms"
    const val STEP_FEATURES = "features"
    const val STEP_AMENITIES = "amenities"
    const val STEP_CONTACT = "contact"
    const val STEP_REVIEW = "review"

    val STEPS = listOf(
        STEP_PROPERTY,
        STEP_ADDRESS,
        STEP_TERMS,
        STEP_FEATURES,
        STEP_AMENITIES,
        STEP_CONTACT,
        STEP_REVIEW,
    )

    // Smallest monthly rent worth a listing, in the tenant's currency.
    const val MIN_RENT = 5000L

    /** The smallest sale price worth a listing, one amount for every property type. */
    const val MIN_SALE_PRICE = 1_000_000L

    /** How far ahead the availability date may be, in days. */
    const val MAX_AVAILABILITY_DAYS = 365L
    private const val MIN_AREA = 5
    private const val MAX_AREA = 1_000_000

    const val NO_PARKING = "0"

    val ROOMS = listOf("1", "2", "3", "4", "5+")
    val PARKINGS = listOf("1", "2", "3+")
    val ROAD_ACCESS = listOf("roadPaved", "trackAccessibleByCar", "trackInaccessibleByCar")

    /** The other boolean amenities of CreateMandateRequest, in form order (prototype's AMENITIES). */
    val AMENITIES = listOf(
        "waterMeter",
        "dedicatedWaterMeter",
        "waterWell",
        "waterTank",
        "electricityMeter",
        "dedicatedElectricityMeter",
        "generatorSet",
        "solarPanel",
        "videoSurveillance",
        "securityGuard",
        "securedFenced",
    )

    /** The category each rental property type belongs to, in form order (prototype's PROPERTY_TYPES). */
    val CATEGORIES_BY_TYPE: Map<PropertyType, PropertyCategory> = linkedMapOf(
        PropertyType.APARTMENT to PropertyCategory.RESIDENTIAL,
        PropertyType.HOUSE to PropertyCategory.RESIDENTIAL,
        PropertyType.ROOM to PropertyCategory.RESIDENTIAL,
        PropertyType.STUDIO to PropertyCategory.RESIDENTIAL,
        PropertyType.DUPLEX to PropertyCategory.RESIDENTIAL,
        PropertyType.BUILDING to PropertyCategory.RESIDENTIAL,
        PropertyType.OFFICE to PropertyCategory.COMMERCIAL,
        PropertyType.RETAIL to PropertyCategory.COMMERCIAL,
        PropertyType.INDUSTRIAL to PropertyCategory.COMMERCIAL,
        PropertyType.LAND to PropertyCategory.COMMERCIAL,
    )

    // A sale drops the room type and offers land for both uses (a plot to build on is residential too).
    private val SALE_CATEGORIES_BY_TYPE: Map<PropertyType, Set<PropertyCategory>> =
        CATEGORIES_BY_TYPE.filterKeys { it != PropertyType.ROOM }.mapValues { (type, category) ->
            if (type == PropertyType.LAND) {
                setOf(PropertyCategory.RESIDENTIAL, PropertyCategory.COMMERCIAL)
            } else {
                setOf(category)
            }
        }

    private val WITHOUT_ROOMS = setOf(PropertyType.ROOM, PropertyType.STUDIO, PropertyType.BUILDING)

    private val TITLE_DOCUMENT_TYPES = listOf(
        TitleDocumentType.LAND_TITLE,
        TitleDocumentType.SURVEY_PLAN,
        TitleDocumentType.CERTIFICATE_OF_OWNERSHIP,
    )

    private fun residential(category: PropertyCategory?) = category == PropertyCategory.RESIDENTIAL

    private fun isSale(listingType: ListingType?): Boolean = listingType == ListingType.SALE

    private fun categoriesByType(listingType: ListingType?): Map<PropertyType, Set<PropertyCategory>> =
        if (isSale(listingType)) {
            SALE_CATEGORIES_BY_TYPE
        } else {
            CATEGORIES_BY_TYPE.mapValues { setOf(it.value) }
        }

    /** The types offered for [category], in form order, for the project. */
    fun propertyTypes(category: PropertyCategory, listingType: ListingType?): List<PropertyType> =
        categoriesByType(listingType).filterValues { category in it }.keys.toList()

    /** Whether [type] is offered for [category] and the project; unknown answers never fit. */
    fun typeFits(category: PropertyCategory?, type: String?, listingType: ListingType?): Boolean {
        val known = FindPropertyRules.parsePropertyType(type) ?: return false
        return categoriesByType(listingType)[known]?.contains(category) == true
    }

    fun listingType(value: ListingType?): String? =
        if (value == null || value == ListingType.UNKNOWN) {
            "Indiquez si vous souhaitez mettre votre bien en location ou le vendre."
        } else {
            null
        }

    /** "Vente" / "Location", the owner's project for every step after the first; rental when unanswered. */
    fun projectLabel(listingType: ListingType?): String =
        if (listingType == ListingType.SALE) "Vente" else "Location"

    // A whole building is let as it is, furnished or not flat by flat; a sale never asks the furnishing.
    fun needsFurniture(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean =
        !isSale(listingType) && residential(category) && type != PropertyType.BUILDING

    // A plot has no bedrooms; a sale never asks them for it.
    fun needsRooms(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean =
        residential(category) && type !in WITHOUT_ROOMS && type != PropertyType.LAND

    // A room is let in a shared home: its parking, if any, isn't its own. A plot has none.
    fun needsParking(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean =
        residential(category) && type != PropertyType.ROOM && type != PropertyType.LAND

    /** A commercial property is always measured; a plot of land sold is sold by its surface. */
    fun needsArea(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean =
        category == PropertyCategory.COMMERCIAL || (isSale(listingType) && type == PropertyType.LAND)

    /** The documents a seller declares, in prototype order: a land sale only, and always optional. */
    fun ownershipDocumentTypes(listingType: ListingType?, type: PropertyType?): List<TitleDocumentType> =
        if (isSale(listingType) && type == PropertyType.LAND) TITLE_DOCUMENT_TYPES else emptyList()

    fun propertyCategory(value: PropertyCategory?): String? =
        if (value == null || value == PropertyCategory.UNKNOWN) {
            "Indiquez si le bien est pour y habiter ou pour une activité."
        } else {
            null
        }

    fun cityId(value: Long?): String? =
        if (value == null || value <= 0) "Choisissez la ville." else null

    fun neighborhoodId(value: Long?): String? =
        if (value == null || value <= 0) "Choisissez le quartier." else null

    fun priceAmount(value: Long?, formatter: TenantFormatter, listingType: ListingType?): String? =
        if (isSale(listingType)) {
            when {
                value == null || value <= 0 -> "Indiquez le prix de vente souhaité."
                value < MIN_SALE_PRICE -> "Indiquez un prix d'au moins ${formatter.money(MIN_SALE_PRICE)}."
                else -> null
            }
        } else {
            when {
                value == null || value <= 0 -> "Indiquez le loyer mensuel souhaité."
                value < MIN_RENT -> "Indiquez un loyer d'au moins ${formatter.money(MIN_RENT)} par mois."
                else -> null
            }
        }

    fun isPriceAmount(value: Long?, listingType: ListingType?): Boolean =
        value != null && value >= if (isSale(listingType)) MIN_SALE_PRICE else MIN_RENT

    /** Optional: no advance asked. */
    fun advanceRent(value: Int?): String? =
        if (value == null || value in RentalTerms.MONTHS) null else "Choisissez une avance proposée."

    /** Optional: no caution asked. */
    fun securityDeposit(value: Int?): String? =
        if (value == null || value in RentalTerms.MONTHS) null else "Indiquez la caution demandée, en mois de loyer."

    /** The date must be between [today] and [MAX_AVAILABILITY_DAYS] days later, so a saved date can expire. */
    fun availableFrom(value: LocalDate?, today: LocalDate): String? = when {
        value == null -> "Indiquez à partir de quand le bien est libre."
        value < today || value > today.plusDays(MAX_AVAILABILITY_DAYS) ->
            "Choisissez une date entre aujourd'hui et dans $MAX_AVAILABILITY_DAYS jours."
        else -> null
    }

    fun propertyType(category: PropertyCategory?, type: String?, listingType: ListingType?): String? =
        if (typeFits(category, type, listingType)) null else "Choisissez le type de bien."

    fun furnitureType(value: String?): String? =
        if (FurnitureType.entries.any { it != FurnitureType.UNKNOWN && it.name == value }) {
            null
        } else {
            "Indiquez si le bien est meublé."
        }

    fun rooms(value: String?): String? = if (value in ROOMS) null else "Indiquez le nombre de chambres."

    const val PARKING_ANSWER_REQUIRED = "Indiquez si le bien a un parking ou un garage."
    const val PARKING_PLACES_REQUIRED = "Indiquez le nombre de places."

    /** [NO_PARKING] ("Non") or a number of places; no answer at all asks whether there is a parking. */
    fun parkings(value: String?): String? = when {
        value.isNullOrEmpty() -> PARKING_ANSWER_REQUIRED
        value == NO_PARKING || value in PARKINGS -> null
        else -> PARKING_PLACES_REQUIRED
    }

    /** A surface: optional unless [required] (a plot of land sold), and when given always usable. */
    fun area(value: Int?, required: Boolean): String? = when {
        value == null && required -> "Indiquez la surface du terrain."
        value == null -> "Indiquez la surface, même approximative."
        value !in MIN_AREA..MAX_AREA -> "Indiquez une surface d'au moins $MIN_AREA m²."
        else -> null
    }

    fun roadAccess(value: String?): String? =
        if (value in ROAD_ACCESS) null else "Indiquez comment on accède au bien."

    fun preferredContactPeriod(value: ContactPeriodType?): String? =
        if (value == null || value == ContactPeriodType.UNKNOWN) {
            "Indiquez quand votre agent peut vous contacter."
        } else {
            null
        }

    /** Strict ISO `yyyy-MM-dd` (what a date input posts); anything else, including "2026-02-31", is null. */
    fun parseDate(value: String?): LocalDate? =
        try {
            value?.trim()?.takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) }?.let { LocalDate.parse(it) }
        } catch (e: DateTimeParseException) {
            null
        }
}
