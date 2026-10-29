package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.npc.service.TenantFormatter

/**
 * Field rules of the "Trouver un bien" wizard, shared by [FindPropertyController] (to validate what a step posts)
 * and [PendingMandate] (to tell which steps are complete). Each returns the French error message, or null when
 * valid. Messages and thresholds are the ones of the `find-property.js` prototype. ndopify-server doesn't re-check
 * any of this yet, so these rules are the only validation a mandate gets.
 */
object FindPropertyRules {
    const val MAX_NEIGHBORHOODS = 3
    const val MAX_COMMENTS = 500

    // Smallest monthly rent worth a search, in the tenant's currency.
    const val MIN_RENT = 5000L
    private const val MIN_AREA = 5
    private const val MAX_AREA = 1_000_000

    // Longer amounts can't be a rent or a surface, and would overflow a Long.
    private const val MAX_AMOUNT_DIGITS = 12

    /** "Indifférent": a UI answer only, sent to the server as null. */
    const val ANY = "ANY"

    val ROOMS = listOf("1", "2", "3", "4", "5+")
    val PARKINGS = listOf("0", "1", "2", "3+", ANY)

    /** The categories each property type can be searched under, in form order (prototype's PROPERTY_TYPES). */
    val CATEGORIES_BY_TYPE: Map<PropertyType, Set<PropertyCategory>> = linkedMapOf(
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
        // Commercial only: nobody rents bare land to live on it (one buys it to build).
        PropertyType.LAND to setOf(PropertyCategory.COMMERCIAL),
    )

    private val WITHOUT_ROOMS = setOf(PropertyType.STUDIO, PropertyType.ROOM)
    private val WITHOUT_FURNITURE = setOf(PropertyType.LAND, PropertyType.BUILDING, PropertyType.INDUSTRIAL)
    private val WITHOUT_PARKINGS = setOf(PropertyType.LAND, PropertyType.ROOM)

    /**
     * The type a form posted or the session kept: null for no answer, "Indifférent" or a value that isn't a type
     * (which [propertyType] rejects). The rules below take the parsed type, never the text.
     */
    fun parsePropertyType(value: String?): PropertyType? =
        PropertyType.entries.firstOrNull { it != PropertyType.UNKNOWN && it.name == value }

    /** A home's bedrooms are asked whatever its type (none and "Indifférent" included), except a studio or a room. */
    fun needsRooms(category: PropertyCategory?, type: PropertyType?): Boolean =
        category == PropertyCategory.RESIDENTIAL && type !in WITHOUT_ROOMS

    fun needsFurniture(type: PropertyType?): Boolean = type !in WITHOUT_FURNITURE

    fun asksParkings(type: PropertyType?): Boolean = type !in WITHOUT_PARKINGS

    /** No type, or "Indifférent", fits any category; otherwise the type must be offered for the category. */
    fun typeFits(category: PropertyCategory?, type: String?): Boolean {
        if (type.isNullOrEmpty() || type == ANY) return true
        val known = parsePropertyType(type) ?: return false
        return CATEGORIES_BY_TYPE[known]?.contains(category) == true
    }

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

    fun propertyType(category: PropertyCategory?, type: String?): String? =
        if (typeFits(category, type)) null else "Choisissez un type de bien proposé."

    fun rooms(value: String?): String? = if (value in ROOMS) null else "Indiquez le nombre de chambres."

    fun furnitureType(value: String?): String? =
        if (value == ANY || FurnitureType.entries.any { it != FurnitureType.UNKNOWN && it.name == value }) {
            null
        } else {
            "Indiquez si le bien doit être meublé."
        }

    fun parkings(value: String?): String? =
        if (value.isNullOrEmpty() || value in PARKINGS) null else "Choisissez un nombre de places proposé."

    fun area(value: Int?): String? =
        if (value == null || value in MIN_AREA..MAX_AREA) {
            null
        } else {
            "Indiquez une surface d'au moins 5 m², ou laissez ce champ vide."
        }

    fun priceAmount(value: Long?, formatter: TenantFormatter): String? = when {
        value == null || value <= 0 -> "Indiquez votre budget mensuel."
        value < MIN_RENT -> "Indiquez un loyer d'au moins ${formatter.money(MIN_RENT)} par mois."
        else -> null
    }

    fun isPriceAmount(value: Long?): Boolean = value != null && value >= MIN_RENT

    fun movingPeriod(value: MovingPeriod?): String? =
        if (value == null || value == MovingPeriod.UNKNOWN) "Indiquez quand vous souhaitez emménager." else null

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
