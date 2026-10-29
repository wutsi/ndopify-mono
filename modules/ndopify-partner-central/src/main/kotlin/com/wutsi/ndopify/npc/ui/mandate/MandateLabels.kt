package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.service.TenantFormatter
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.util.Locale

/**
 * The French wording of a mandate, shared by the find-property review, the Espace agent's offers and mandates, and
 * the mandate pages.
 */
object MandateLabels {
    // Labels of the prototype (find-property.js).
    val PROPERTY_TYPE_LABELS: Map<PropertyType, String> = linkedMapOf(
        PropertyType.ROOM to "Chambre",
        PropertyType.STUDIO to "Studio",
        PropertyType.APARTMENT to "Appartement",
        PropertyType.HOUSE to "Maison",
        PropertyType.VILLA to "Villa",
        PropertyType.DUPLEX to "Duplex",
        PropertyType.RETAIL to "Boutique, local commercial",
        PropertyType.OFFICE to "Bureau",
        PropertyType.INDUSTRIAL to "Entrepôt, local industriel",
        PropertyType.BUILDING to "Immeuble",
        PropertyType.LAND to "Terrain",
    )

    val ROOM_LABELS = mapOf(
        "1" to "1 chambre",
        "2" to "2 chambres",
        "3" to "3 chambres",
        "4" to "4 chambres",
        "5+" to "5 chambres et +",
    )

    val FURNITURE_LABELS = mapOf(
        FurnitureType.FULLY_FURNISHED.name to "Meublé",
        FurnitureType.UNFURNISHED.name to "Non meublé",
        FindPropertyRules.ANY to "Meublé ou non",
    )

    val PARKING_LABELS = mapOf(
        "0" to "Sans parking",
        "1" to "1 place de parking",
        "2" to "2 places de parking",
        "3+" to "3 places de parking et +",
        FindPropertyRules.ANY to "Parking indifférent",
    )

    val MOVE_IN_LABELS = mapOf(
        MovingPeriod.WITHIN_1_MONTH to "Dans moins d'1 mois",
        MovingPeriod.WITHIN_3_MONTHS to "Dans 1 à 3 mois",
        MovingPeriod.AFTER_3_MONTHS to "Dans plus de 3 mois",
    )

    /** When the customer wants to be called, as "à appeler …" ends it. */
    val CONTACT_PERIOD_LABELS = mapOf(
        ContactPeriodType.MORNING to "le matin",
        ContactPeriodType.AFTERNOON to "l'après-midi",
        ContactPeriodType.EVENING to "le soir",
    )

    /** The prototype's AMENITIES, by [Amenity.key]. */
    val AMENITY_LABELS: Map<String, String> = Amenity.LABELS

    // In a title ("Locataire · Boutique"): one word (prototype's PROPERTY_TYPE_SHORT).
    private val PROPERTY_TYPE_SHORT = mapOf(
        PropertyType.RETAIL to "Boutique",
        PropertyType.INDUSTRIAL to "Entrepôt",
    )

    // The criteria's "Parking" row (prototype's PARKING_LABELS in partner-central.js); other values are shown as is.
    private val PARKING_VALUES = mapOf("0" to "Aucune")

    private val MANDATE_TYPE_LABELS = mapOf(
        MandateType.SEARCH to "Recherche",
        MandateType.LISTING to "Affichage"
    )

    private val CATEGORY_NAMES = mapOf(
        PropertyCategory.RESIDENTIAL to "Résidentiel",
        PropertyCategory.COMMERCIAL to "Commercial",
    )

    /** "150 000", with a non-breaking space so an amount never wraps. */
    fun formatNumber(value: Number): String =
        String.format(Locale.FRANCE, "%,d", value.toLong()).replace(' ', ' ')

    fun mandateType(type: MandateType): String = MANDATE_TYPE_LABELS[type] ?: "Mandat"

    fun category(category: PropertyCategory): String? = CATEGORY_NAMES[category]

    fun propertyType(type: PropertyType?): String = PROPERTY_TYPE_LABELS[type] ?: "Tout type de bien"

    fun furniture(type: FurnitureType?): String = when (type) {
        FurnitureType.FULLY_FURNISHED -> "Oui"
        FurnitureType.UNFURNISHED -> "Non"
        else -> "Indifférent"
    }

    /**
     * "150 000 FCFA / mois" in the tenant's format, or "150K FCFA / mois" when [compact] (a card's facts); without
     * " / mois" unless the price is monthly.
     */
    fun price(money: Money, formatter: TenantFormatter, compact: Boolean = false): String {
        val amount = if (compact) {
            formatter.compactMoney(money.amount, money.currency)
        } else {
            formatter.money(money.amount, money.currency)
        }
        return if (money.periodicity == PeriodicityType.MONTHLY) "$amount / mois" else amount
    }

    /** The mandate's criteria by group ("Accès à l'eau" → "Compteur d'eau, Forage"), only the groups it has. */
    fun amenityGroups(mandate: Mandate): List<Pair<String, String>> =
        Amenity.of(mandate).groupBy { it.group }.map { (group, amenities) ->
            group to amenities.joinToString(", ") { it.label }
        }

    /** Who the agent works for: a landlord for a listing, else someone looking for a rental. */
    fun party(type: MandateType): String = if (type == MandateType.LISTING) "Bailleur" else "Locataire"

    /** null without a type: any type will do. */
    fun shortPropertyType(type: PropertyType?): String? = PROPERTY_TYPE_SHORT[type] ?: PROPERTY_TYPE_LABELS[type]

    /** "Locataire · Maison", or just "Locataire" without a type. */
    fun title(mandate: Mandate): String =
        listOfNotNull(party(mandate.mandateType), shortPropertyType(mandate.propertyType)).joinToString(" · ")

    /**
     * The mandate's criteria as term → value rows, in the prototype's order (`criteriaOf`): sector, amount and moving
     * date first (a card's three facts), the category and the type (with them, the page's key criteria), then the
     * follow-up questions that apply to the type, the amenities by group, and the comments.
     */
    fun criteria(
        mandate: Mandate,
        locations: Map<Long, Location>,
        formatter: TenantFormatter
    ): List<Pair<String, String>> {
        val owner = mandate.mandateType == MandateType.LISTING
        val type = mandate.propertyType
        val neighborhoods = mandate.neighborhoodIds.mapNotNull { locations[it]?.name }
        val sector = listOfNotNull(
            if (mandate.neighborhoodIds.isEmpty()) {
                "Toute la ville"
            } else {
                neighborhoods.joinToString(", ")
                    .ifEmpty { null }
            },
            locations[mandate.cityId]?.name,
        ).joinToString(" — ").ifEmpty { NONE }
        return listOfNotNull(
            (if (mandate.neighborhoodIds.size > 1) "Secteurs" else "Secteur") to sector,
            (if (owner) "Loyer" else "Budget") to price(mandate.price, formatter),
            (if (owner) "Disponibilité" else "Emménagement") to (
                mandate.availableFrom?.takeIf { owner }?.let { "Dès le " + formatter.date(it) }
                    ?: MOVE_IN_LABELS[mandate.movingPeriod] ?: NONE
                ),
            "Catégorie" to (category(mandate.propertyCategory) ?: NONE),
            "Type de propriété" to (PROPERTY_TYPE_LABELS[mandate.propertyType] ?: "Indifférent"),
            ("Meublé" to furniture(mandate.furnitureType)).takeIf { FindPropertyRules.needsFurniture(type) },
            mandate.rooms?.let { "Chambres" to it }
                ?.takeIf { FindPropertyRules.needsRooms(mandate.propertyCategory, type) },
            ("Parking" to (mandate.parkings?.let { PARKING_VALUES[it] ?: it } ?: "Indifférent"))
                .takeIf { FindPropertyRules.asksParkings(type) },
            mandate.area?.let { (if (owner) "Surface" else "Surface souhaitée") to formatNumber(it) + " m²" },
        ) + amenityGroups(mandate) + listOfNotNull(mandate.comments?.trim()?.ifEmpty { null }
            ?.let { "Autres précisions" to it })
    }

    private const val NONE = "—"
}
