package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.FinancingType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.web.core.service.TenantFormatter

/**
 * Field rules of the "Trouver un bien" wizard, shared by [FindPropertyController] (to validate what a step posts)
 * and [PendingMandate] (to tell which steps are complete). Each returns the French error message, or null when
 * valid. Messages and thresholds are the ones of the `find-property.js` prototype. ndopify-server doesn't re-check
 * any of this yet, so these rules are the only validation a mandate gets.
 *
 * The wizard serves two projects: a rental ([ListingType.RENTAL]) and a purchase ([ListingType.SALE]). Land is
 * bought, never rented: only a purchase offers it, under the residential use only and no room, asks no furniture,
 * requires the surface of a plot of land and its optional documents, has its own budget floor and asks how the buyer
 * will pay.
 */
object FindPropertyRules {
    const val MAX_NEIGHBORHOODS = 3
    const val MAX_COMMENTS = 500

    // Smallest monthly rent worth a search, in the tenant's currency.
    const val MIN_RENT = 5000L

    /** The smallest purchase worth a search, one amount for every property type. */
    const val MIN_SALE_PRICE = 1_000_000L

    private const val MIN_AREA = 5
    private const val MAX_AREA = 1_000_000

    // Longer amounts can't be a rent or a surface, and would overflow a Long.
    private const val MAX_AMOUNT_DIGITS = 12

    /** "Indifférent": a UI answer only, sent to the server as null. */
    const val ANY = "ANY"

    val ROOMS = listOf("1", "2", "3", "4", "5+")
    val PARKINGS = listOf("0", "1", "2", "3+", ANY)

    /**
     * The categories each property type can be searched under, in form order (prototype's PROPERTY_TYPES). A rental
     * offers no land: nobody rents bare land to live on it (one buys it to build).
     */
    private val RENTAL_CATEGORIES_BY_TYPE: Map<PropertyType, Set<PropertyCategory>> = linkedMapOf(
        PropertyType.ROOM to setOf(PropertyCategory.RESIDENTIAL),
        PropertyType.STUDIO to setOf(PropertyCategory.RESIDENTIAL),
        PropertyType.APARTMENT to setOf(PropertyCategory.RESIDENTIAL),
        PropertyType.HOUSE to setOf(PropertyCategory.RESIDENTIAL),
        PropertyType.VILLA to setOf(PropertyCategory.RESIDENTIAL),
        PropertyType.DUPLEX to setOf(PropertyCategory.RESIDENTIAL),
        PropertyType.RETAIL to setOf(PropertyCategory.COMMERCIAL),
        PropertyType.OFFICE to setOf(PropertyCategory.COMMERCIAL),
        PropertyType.INDUSTRIAL to setOf(PropertyCategory.COMMERCIAL),
        PropertyType.BUILDING to setOf(PropertyCategory.COMMERCIAL),
    )

    // A purchase drops the room type and offers land under the residential use only (a plot to build on is
    // residential); a rental never offers land.
    private val SALE_CATEGORIES_BY_TYPE: Map<PropertyType, Set<PropertyCategory>> =
        RENTAL_CATEGORIES_BY_TYPE - PropertyType.ROOM +
            (PropertyType.LAND to setOf(PropertyCategory.RESIDENTIAL))

    private val RENTAL_WITHOUT_ROOMS = setOf(PropertyType.STUDIO, PropertyType.ROOM)
    private val SALE_WITH_ROOMS =
        setOf(PropertyType.APARTMENT, PropertyType.HOUSE, PropertyType.VILLA, PropertyType.DUPLEX)
    private val WITHOUT_FURNITURE = setOf(PropertyType.LAND, PropertyType.BUILDING, PropertyType.INDUSTRIAL)
    private val WITHOUT_PARKINGS = setOf(PropertyType.LAND, PropertyType.ROOM)

    private val TITLE_DOCUMENT_TYPES = listOf(
        TitleDocumentType.LAND_TITLE,
        TitleDocumentType.SURVEY_PLAN,
        TitleDocumentType.CERTIFICATE_OF_OWNERSHIP,
    )

    fun isSale(listingType: ListingType?): Boolean = listingType == ListingType.SALE

    /** The project a link's `?type=` preselects: SALE or RENTAL by name, null for anything else (never UNKNOWN). */
    fun parseListingType(value: String?): ListingType? =
        ListingType.entries.firstOrNull { it != ListingType.UNKNOWN && it.name == value }

    /**
     * The type a form posted or the session kept: null for no answer, "Indifférent" or a value that isn't a type
     * (which [propertyType] rejects). The rules below take the parsed type, never the text.
     */
    fun parsePropertyType(value: String?): PropertyType? =
        PropertyType.entries.firstOrNull { it != PropertyType.UNKNOWN && it.name == value }

    /** The types offered for [category], in form order, for the project. */
    fun propertyTypes(category: PropertyCategory, listingType: ListingType?): List<PropertyType> =
        categoriesByType(listingType).filterValues { it.contains(category) }.keys.toList()

    private fun categoriesByType(listingType: ListingType?): Map<PropertyType, Set<PropertyCategory>> =
        if (isSale(listingType)) SALE_CATEGORIES_BY_TYPE else RENTAL_CATEGORIES_BY_TYPE

    /**
     * A home's bedrooms are asked whatever its type (none and "Indifférent" included), except a plot of land, a
     * studio or a room.
     */
    fun needsRooms(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean {
        if (category != PropertyCategory.RESIDENTIAL || type == PropertyType.LAND) return false
        if (isSale(listingType)) return type == null || type in SALE_WITH_ROOMS
        return type !in RENTAL_WITHOUT_ROOMS
    }

    /** A purchase isn't searched by its furniture: the buyer discusses it with the seller. */
    fun needsFurniture(type: PropertyType?, listingType: ListingType?): Boolean =
        !isSale(listingType) && type !in WITHOUT_FURNITURE

    fun asksParkings(type: PropertyType?): Boolean = type !in WITHOUT_PARKINGS

    /** A plot of land bought is searched by its size: its surface is required. */
    fun needsArea(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean =
        isSale(listingType) && type == PropertyType.LAND

    /** The documents a buyer may require, in prototype order: a land purchase only, and always optional. */
    fun titleDocumentTypes(listingType: ListingType?, type: PropertyType?): List<TitleDocumentType> =
        if (isSale(listingType) && type == PropertyType.LAND) TITLE_DOCUMENT_TYPES else emptyList()

    /** No type, or "Indifférent", fits any category; otherwise the type must be offered for the category and project. */
    fun typeFits(category: PropertyCategory?, type: String?, listingType: ListingType?): Boolean {
        if (type.isNullOrEmpty() || type == ANY) return true
        val known = parsePropertyType(type) ?: return false
        return categoriesByType(listingType)[known]?.contains(category) == true
    }

    fun listingType(value: ListingType?): String? =
        if (value == null || value == ListingType.UNKNOWN) "Indiquez si vous souhaitez louer ou acheter." else null

    /** "Achat" / "Location", the buyer's project for every step after the first; rental when unanswered. */
    fun projectLabel(listingType: ListingType?): String =
        if (listingType == ListingType.SALE) "Achat" else "Location"

    fun propertyCategory(value: PropertyCategory?): String? =
        if (value == null || value == PropertyCategory.UNKNOWN) {
            "Indiquez si le bien est pour y habiter ou pour une activité."
        } else {
            null
        }

    fun cityId(value: Long?): String? =
        if (value == null || value <= 0) "Choisissez la ville." else null

    /** Optional: without neighbourhoods, the whole city is searched. */
    fun neighborhoodIds(value: List<Long>): String? =
        if (value.size > MAX_NEIGHBORHOODS) "Choisissez au plus $MAX_NEIGHBORHOODS quartiers." else null

    fun propertyType(category: PropertyCategory?, type: String?, listingType: ListingType?): String? =
        if (typeFits(category, type, listingType)) null else "Choisissez un type de bien proposé."

    fun rooms(value: String?): String? = if (value in ROOMS) null else "Indiquez le nombre de chambres."

    fun furnitureType(value: String?): String? =
        if (value == ANY || FurnitureType.entries.any { it != FurnitureType.UNKNOWN && it.name == value }) {
            null
        } else {
            "Indiquez si le bien doit être meublé."
        }

    fun parkings(value: String?): String? =
        if (value.isNullOrEmpty() || value in PARKINGS) null else "Choisissez un nombre de places proposé."

    /**
     * A surface: optional unless [required] (a plot of land bought), and when given always usable. A value typed that
     * isn't a number is an unusable value too.
     */
    fun area(value: Int?, required: Boolean): String? = when {
        value != null && value in MIN_AREA..MAX_AREA -> null
        required && value == null -> "Indiquez la surface du terrain."
        required -> "Indiquez une surface d'au moins 5 m²."
        value == null -> null
        else -> "Indiquez une surface d'au moins 5 m², ou laissez ce champ vide."
    }

    fun priceAmount(value: Long?, formatter: TenantFormatter, listingType: ListingType?): String? =
        if (isSale(listingType)) {
            when {
                value == null || value <= 0 -> "Indiquez votre budget total."
                value < MIN_SALE_PRICE -> "Indiquez un budget d'au moins ${formatter.money(MIN_SALE_PRICE)}."
                else -> null
            }
        } else {
            when {
                value == null || value <= 0 -> "Indiquez votre budget mensuel."
                value < MIN_RENT -> "Indiquez un loyer d'au moins ${formatter.money(MIN_RENT)} par mois."
                else -> null
            }
        }

    fun isPriceAmount(value: Long?, listingType: ListingType?): Boolean =
        value != null && value >= if (isSale(listingType)) MIN_SALE_PRICE else MIN_RENT

    fun movingPeriod(value: MovingPeriod?, listingType: ListingType?): String? =
        when {
            value != null && value != MovingPeriod.UNKNOWN -> null
            isSale(listingType) -> "Indiquez quand vous souhaitez acheter."
            else -> "Indiquez quand vous souhaitez emménager."
        }

    /** A purchase sends how it will be financed; a rental asks nothing. */
    fun financingType(value: FinancingType?, listingType: ListingType?): String? =
        if (!isSale(listingType) || (value != null && value != FinancingType.UNKNOWN)) {
            null
        } else {
            "Indiquez comment vous financerez votre achat."
        }

    fun preferredContactPeriod(value: ContactPeriodType?): String? =
        if (value == null || value == ContactPeriodType.UNKNOWN) {
            "Indiquez quand votre agent peut vous contacter."
        } else {
            null
        }

    fun comments(value: String?): String? =
        if ((value?.length
                ?: 0) > MAX_COMMENTS
        ) {
            "Votre message ne doit pas dépasser $MAX_COMMENTS caractères."
        } else {
            null
        }

    /** "150 000", "150 000" or "150.000" → 150000. Blank, non-numeric or out-of-range → null. */
    fun parseAmount(value: String?): Long? =
        value.orEmpty()
            .filter { it.isDigit() }
            .takeIf { it.isNotEmpty() && it.length <= MAX_AMOUNT_DIGITS }
            ?.toLong()
}
