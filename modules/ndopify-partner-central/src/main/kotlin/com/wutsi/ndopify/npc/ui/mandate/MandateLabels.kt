package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.math.BigDecimal
import java.math.RoundingMode
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
        "FULLY_FURNISHED" to "Meublé",
        "UNFURNISHED" to "Non meublé",
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

    /** Same order and copy as the prototype's AMENITIES; keys are [PendingMandate.AMENITIES]. */
    val AMENITY_LABELS: Map<String, String> = linkedMapOf(
        "waterMeter" to "Compteur d'eau",
        "dedicatedWaterMeter" to "Compteur d'eau individuel",
        "waterTank" to "Citerne",
        "waterWell" to "Forage",
        "electricityMeter" to "Compteur électrique",
        "dedicatedElectricityMeter" to "Compteur électrique individuel",
        "generatorSet" to "Groupe électrogène",
        "solarPanel" to "Panneaux solaires",
        "roadPaved" to "Route bitumée",
        "trackAccessibleByCar" to "Piste accessible en voiture",
        "trackInaccessibleByCar" to "Piste accessible à pied seulement",
        "securedFenced" to "Clôturé et sécurisé",
        "securityGuard" to "Gardien",
        "videoSurveillance" to "Vidéosurveillance",
    )

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

    // The prototype's "Voir les détails" rows (index.html); keys are AMENITY_LABELS'.
    private val AMENITY_GROUPS = linkedMapOf(
        "Accès à l'eau" to listOf("waterMeter", "dedicatedWaterMeter", "waterTank", "waterWell"),
        "Accès à l'électricité" to listOf(
            "electricityMeter",
            "dedicatedElectricityMeter",
            "generatorSet",
            "solarPanel"
        ),
        "Accès routier" to listOf("roadPaved", "trackAccessibleByCar", "trackInaccessibleByCar"),
        "Sécurité" to listOf("securedFenced", "securityGuard", "videoSurveillance"),
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

    /** "150K FCFA par mois". */
    fun price(money: Money): String {
        val currency = if (money.currency == "XAF") "FCFA" else money.currency
        val period = if (money.periodicity == PeriodicityType.MONTHLY) " par mois" else ""
        return "${formatAmount(money.amount)} $currency$period"
    }

    /** An amount to read, not to type: "800K", "1.5M" (FCFA has no cents, so K and M are enough). */
    fun formatAmount(value: Number): String {
        val amount = value.toLong()
        return when {
            amount >= 1_000_000 -> short(amount, 1_000_000) + "M"
            amount >= 1_000 -> short(amount, 1_000) + "K"
            else -> amount.toString()
        }
    }

    private fun short(amount: Long, unit: Long): String =
        BigDecimal.valueOf(amount)
            .divide(BigDecimal.valueOf(unit), 2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    /** The mandate's criteria by group ("Accès à l'eau" → "Compteur d'eau, Forage"), only the groups it has. */
    fun amenityGroups(mandate: Mandate): List<Pair<String, String>> {
        val checked = mapOf(
            "waterMeter" to mandate.waterMeter,
            "dedicatedWaterMeter" to mandate.dedicatedWaterMeter,
            "waterTank" to mandate.waterTank,
            "waterWell" to mandate.waterWell,
            "electricityMeter" to mandate.electricityMeter,
            "dedicatedElectricityMeter" to mandate.dedicatedElectricityMeter,
            "generatorSet" to mandate.generatorSet,
            "solarPanel" to mandate.solarPanel,
            "roadPaved" to mandate.roadPaved,
            "trackAccessibleByCar" to mandate.trackAccessibleByCar,
            "trackInaccessibleByCar" to mandate.trackInaccessibleByCar,
            "securedFenced" to mandate.securedFenced,
            "securityGuard" to mandate.securityGuard,
            "videoSurveillance" to mandate.videoSurveillance,
        )
        return AMENITY_GROUPS.mapNotNull { (group, keys) ->
            val labels = keys.filter { checked[it] == true }.mapNotNull { AMENITY_LABELS[it] }
            if (labels.isEmpty()) null else group to labels.joinToString(", ")
        }
    }

    /** Who the agent works for: a landlord for a listing, else someone looking for a rental. */
    fun party(type: MandateType): String = if (type == MandateType.LISTING) "Bailleur" else "Locataire"

    fun shortPropertyType(type: PropertyType?): String =
        PROPERTY_TYPE_SHORT[type] ?: PROPERTY_TYPE_LABELS[type] ?: "Tout type"

    /** "Locataire · Maison". */
    fun title(mandate: Mandate): String = party(mandate.mandateType) + " · " + shortPropertyType(mandate.propertyType)

    /** "150K FCFA / mois", "152 500 FCFA / mois": the rent of a mandate's criteria. */
    fun rent(money: Money): String {
        val value = if (money.amount % 1000 == 0L) "${money.amount / 1000}K" else formatNumber(money.amount)
        val currency = if (money.currency == "XAF") "FCFA" else money.currency
        return "$value $currency / mois"
    }

    /**
     * The mandate's criteria as term → value rows, in the prototype's order (`criteriaOf`): sector, amount and moving
     * date first (a card's three facts), the category and the type (with them, the page's key criteria), then the
     * follow-up questions that apply to the type, the amenities by group, and the comments.
     */
    fun criteria(mandate: Mandate, locations: Map<Long, Location>): List<Pair<String, String>> {
        val owner = mandate.mandateType == MandateType.LISTING
        val type = mandate.propertyType?.name
        val neighborhoods = mandate.neighborhoodIds.mapNotNull { locations[it]?.name }
        val sector = listOfNotNull(
            if (mandate.neighborhoodIds.isEmpty()) "Toute la ville" else neighborhoods.joinToString(", ").ifEmpty { null },
            locations[mandate.cityId]?.name,
        ).joinToString(" — ").ifEmpty { NONE }
        return listOfNotNull(
            (if (mandate.neighborhoodIds.size > 1) "Secteurs" else "Secteur") to sector,
            (if (owner) "Loyer" else "Budget") to rent(mandate.price),
            (if (owner) "Disponibilité" else "Emménagement") to (MOVE_IN_LABELS[mandate.movingPeriod] ?: NONE),
            "Catégorie" to (category(mandate.propertyCategory) ?: NONE),
            "Type de propriété" to (PROPERTY_TYPE_LABELS[mandate.propertyType] ?: "Indifférent"),
            ("Meublé" to furniture(mandate.furnitureType)).takeIf { FindPropertyRules.needsFurniture(type) },
            mandate.rooms?.let { "Chambres" to it }?.takeIf { FindPropertyRules.needsRooms(type) },
            ("Parking" to (mandate.parkings?.let { PARKING_VALUES[it] ?: it } ?: "Indifférent"))
                .takeIf { FindPropertyRules.asksParkings(type) },
            mandate.area?.let { (if (owner) "Surface" else "Surface souhaitée") to formatNumber(it) + " m²" },
        ) + amenityGroups(mandate) + listOfNotNull(mandate.comments?.trim()?.ifEmpty { null }?.let { "Autres précisions" to it })
    }

    private const val NONE = "—"
}
