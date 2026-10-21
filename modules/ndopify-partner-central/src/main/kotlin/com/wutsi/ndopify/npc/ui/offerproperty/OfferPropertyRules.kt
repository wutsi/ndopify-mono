package com.wutsi.ndopify.npc.ui.offerproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.npc.service.TenantFormatter
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Field rules of the "Proposer un bien" wizard, shared by its controller (to validate what a step posts) and
 * [PendingListing] (to tell which steps are complete). Each returns the French error message, or null when valid.
 * Messages and thresholds are the ones of the `offer-property.js` prototype. ndopify-server doesn't re-check any of
 * this yet, so these rules are the only validation a listing gets.
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
    const val MAX_ADVANCE_MONTHS = 12
    const val MAX_DEPOSIT_MONTHS = 6

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

    /** The category each offered property type belongs to, in form order (prototype's PROPERTY_TYPES). */
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

    private val WITHOUT_ROOMS = setOf("ROOM", "STUDIO", "BUILDING")

    private fun residential(category: PropertyCategory?) = category == PropertyCategory.RESIDENTIAL

    // A whole building is let as it is, furnished or not flat by flat.
    fun needsFurniture(category: PropertyCategory?, type: String?): Boolean =
        residential(category) && type != "BUILDING"

    fun needsRooms(category: PropertyCategory?, type: String?): Boolean =
        residential(category) && type !in WITHOUT_ROOMS

    // A room is let in a shared home: its parking, if any, isn't its own.
    fun needsParking(category: PropertyCategory?, type: String?): Boolean =
        residential(category) && type != "ROOM"

    fun needsArea(category: PropertyCategory?): Boolean = category == PropertyCategory.COMMERCIAL

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

    fun priceAmount(value: Long?, formatter: TenantFormatter): String? = when {
        value == null || value <= 0 -> "Indiquez le loyer mensuel souhaité."
        value < MIN_RENT -> "Indiquez un loyer d'au moins ${formatter.money(MIN_RENT)} par mois."
        else -> null
    }

    fun isPriceAmount(value: Long?): Boolean = value != null && value >= MIN_RENT

    /** Optional: no advance asked. */
    fun advanceRent(value: Int?): String? =
        if (value == null || value in 1..MAX_ADVANCE_MONTHS) null else "Choisissez une avance proposée."

    fun securityDeposit(value: Int?): String? =
        if (value != null && value in 1..MAX_DEPOSIT_MONTHS) null else "Indiquez la caution demandée, en mois de loyer."

    /** The date must be between [today] and [MAX_AVAILABILITY_DAYS] days later, so a saved date can expire. */
    fun availableFrom(value: LocalDate?, today: LocalDate): String? = when {
        value == null -> "Indiquez à partir de quand le bien est libre."
        value < today || value > today.plusDays(MAX_AVAILABILITY_DAYS) ->
            "Choisissez une date entre aujourd'hui et dans $MAX_AVAILABILITY_DAYS jours."
        else -> null
    }

    fun propertyType(category: PropertyCategory?, type: String?): String? {
        val known = CATEGORIES_BY_TYPE.keys.firstOrNull { it.name == type }
        return if (known != null && CATEGORIES_BY_TYPE[known] == category) null else "Choisissez le type de bien."
    }

    fun furnitureType(value: String?): String? =
        if (FurnitureType.entries.any { it != FurnitureType.UNKNOWN && it.name == value }) {
            null
        } else {
            "Indiquez si le bien est meublé."
        }

    fun rooms(value: String?): String? = if (value in ROOMS) null else "Indiquez le nombre de chambres."

    /** [NO_PARKING] ("Non") or a number of places; no answer at all asks whether there is a parking. */
    fun parkings(value: String?): String? = when {
        value.isNullOrEmpty() -> "Indiquez si le bien a un parking ou un garage."
        value == NO_PARKING || value in PARKINGS -> null
        else -> "Indiquez le nombre de places."
    }

    fun area(value: Int?): String? = when {
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
