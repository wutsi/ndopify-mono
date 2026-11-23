package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.FinancingType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.web.core.service.TenantFormatter
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

    val FURNITURE_TYPE_LABELS: Map<FurnitureType, String> = linkedMapOf(
        FurnitureType.FULLY_FURNISHED to "Meublé",
        FurnitureType.UNFURNISHED to "Non meublé",
    )

    /** [FURNITURE_TYPE_LABELS] by name, with the wizard's [FindPropertyRules.ANY]. */
    val FURNITURE_LABELS: Map<String, String> =
        byName(FURNITURE_TYPE_LABELS) + (FindPropertyRules.ANY to "Meublé ou non")

    val PARKING_LABELS = mapOf(
        "0" to "Sans parking",
        "1" to "1 place de parking",
        "2" to "2 places de parking",
        "3+" to "3 places de parking et +",
        FindPropertyRules.ANY to "Parking indifférent",
    )

    val MOVE_IN_LABELS = mapOf(
        MovingPeriod.WITHIN_1_MONTH to "Dans moins d'1\u00a0mois",
        MovingPeriod.WITHIN_3_MONTHS to "Dans 1 à 3\u00a0mois",
        MovingPeriod.AFTER_3_MONTHS to "Dans plus de 3\u00a0mois",
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

    // The wizards' review, after "Location, " (prototype's find-property.js / offer-property.js).
    private val PROJECT_LABELS = mapOf(
        PropertyCategory.RESIDENTIAL to "usage résidentiel",
        PropertyCategory.COMMERCIAL to "usage commercial",
    )

    val CATEGORY_LABELS: Map<PropertyCategory, String> = linkedMapOf(
        PropertyCategory.RESIDENTIAL to "Résidentiel",
        PropertyCategory.COMMERCIAL to "Commercial",
    )

    /** The step's verb, not the mandate's noun (prototype's LISTING_TYPE_LABELS). */
    val LISTING_TYPE_LABELS: Map<ListingType, String> = linkedMapOf(
        ListingType.RENTAL to "Louer",
        ListingType.SALE to "Acheter",
    )

    /** An owner's project on *Proposer un bien*: the same enum, the owner's words (prototype's listing type labels). */
    val OWNER_LISTING_TYPE_LABELS: Map<ListingType, String> = linkedMapOf(
        ListingType.RENTAL to "Mettre en location",
        ListingType.SALE to "Vendre",
    )

    /** How a purchase will be paid (prototype's FINANCING_LABELS). */
    val FINANCING_LABELS: Map<FinancingType, String> = linkedMapOf(
        FinancingType.CASH to "Comptant",
        FinancingType.BANK_LOAN to "Prêt bancaire",
        FinancingType.TRANSFER_FROM_ABROAD to "Transfert depuis l'étranger",
        FinancingType.UNDECIDED to "Pas encore décidé",
    )

    /** The land documents a buyer may require (prototype's TITLE_DOCUMENT_LABELS). */
    val TITLE_DOCUMENT_LABELS: Map<TitleDocumentType, String> = linkedMapOf(
        TitleDocumentType.LAND_TITLE to "Titre foncier",
        TitleDocumentType.SURVEY_PLAN to "Plan de bornage",
        TitleDocumentType.CERTIFICATE_OF_OWNERSHIP to "Certificat de propriété",
    )

    /** [labels] keyed by the enum's name: what a template looks up with a form's value (`labels['ROOM']`). */
    fun <E : Enum<E>> byName(labels: Map<E, String>): Map<String, String> = labels.mapKeys { it.key.name }

    /** "150 000", with a non-breaking space so an amount never wraps. */
    fun formatNumber(value: Number): String =
        String.format(Locale.FRANCE, "%,d", value.toLong()).replace(' ', ' ')

    fun category(category: PropertyCategory): String? = CATEGORY_LABELS[category]

    /** "Location, usage résidentiel" or "Achat, usage résidentiel", the wizards' project; null without a category. */
    fun project(category: PropertyCategory?, listingType: ListingType?): String? =
        PROJECT_LABELS[category]?.let { (if (FindPropertyRules.isSale(listingType)) "Achat, " else "Location, ") + it }

    /** An owner's project: "Vente, usage résidentiel" for a sale, "Location, usage résidentiel" otherwise. */
    fun ownerProject(category: PropertyCategory?, listingType: ListingType?): String? =
        PROJECT_LABELS[category]?.let { (if (FindPropertyRules.isSale(listingType)) "Vente, " else "Location, ") + it }

    /** The review's horizon term: a purchase is planned, a rental is moved into (or installed for an activity). */
    fun moveInTerm(listingType: ListingType?, category: PropertyCategory?): String = when {
        FindPropertyRules.isSale(listingType) -> "Achat prévu"
        category == PropertyCategory.COMMERCIAL -> "Date d'installation"
        else -> "Date d'emménagement"
    }

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

    /**
     * Who the agent works for, the first word of a mandate's title: a seller or a buyer for a sale ("Vendeur" /
     * "Acheteur"), else an owner for a listing ("Propriétaire", the public site's word, ndopify-web's menu) or someone
     * looking for a rental.
     */
    fun party(type: MandateType, listingType: ListingType? = null): String = when {
        FindPropertyRules.isSale(listingType) -> if (type == MandateType.LISTING) "Vendeur" else "Acheteur"
        type == MandateType.LISTING -> "Propriétaire"
        else -> "Locataire"
    }

    /** The amount row of the criteria: a seller's asking price, a landlord's rent, a search's budget. */
    fun amountTerm(owner: Boolean, listingType: ListingType?): String = when {
        FindPropertyRules.isSale(listingType) && owner -> "Prix"
        owner -> "Loyer"
        else -> "Budget"
    }

    /** The amount of a listing, as the match cards name it: a sale price, else a rent. */
    fun listingPriceTerm(listingType: ListingType?): String =
        if (FindPropertyRules.isSale(listingType)) "Prix" else "Loyer"

    /** The date row of the criteria: a landlord's availability, a buyer's purchase, a renter's move. */
    fun dateTerm(owner: Boolean, listingType: ListingType?): String = when {
        owner -> "Disponibilité"
        FindPropertyRules.isSale(listingType) -> "Achat prévu"
        else -> "Emménagement"
    }

    /** null without a type: any type will do. */
    fun shortPropertyType(type: PropertyType?): String? = PROPERTY_TYPE_SHORT[type] ?: PROPERTY_TYPE_LABELS[type]

    /** "Vendeur · Terrain", or just "Vendeur" without a type. */
    fun title(mandate: Mandate): String =
        listOfNotNull(party(mandate.mandateType, mandate.listingType), shortPropertyType(mandate.propertyType))
            .joinToString(" · ")

    /** "Titre foncier · Plan de bornage", in the stored order; "Aucun document exigé" for none. */
    fun titleDocuments(documents: List<TitleDocumentType>): String =
        documents.mapNotNull { TITLE_DOCUMENT_LABELS[it] }.joinToString(" · ").ifEmpty { "Aucun document exigé" }

    /** "Titre foncier · Plan de bornage", in the stored order; "Aucun document déclaré" for none. */
    fun ownershipDocuments(documents: List<TitleDocumentType>): String =
        documents.mapNotNull { TITLE_DOCUMENT_LABELS[it] }.joinToString(" · ").ifEmpty { "Aucun document déclaré" }

    /**
     * The mandate's criteria as term → value rows, in the prototype's order (`criteriaOf`): sector, amount and moving
     * date first (a card's three facts), the category and the type (with them, the page's key criteria), then the
     * follow-up questions that apply to the type, the amenities by group, and the comments. A purchase has no
     * furniture row, and adds the buyer's financing and the documents it requires for a plot of land.
     */
    fun criteria(
        mandate: Mandate,
        locations: Map<Long, Location>,
        formatter: TenantFormatter
    ): List<Pair<String, String>> {
        val owner = mandate.mandateType == MandateType.LISTING
        val sale = FindPropertyRules.isSale(mandate.listingType)
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
            amountTerm(owner, mandate.listingType) to price(mandate.price, formatter),
            dateTerm(owner, mandate.listingType) to (
                mandate.availableFrom?.takeIf { owner }?.let { "Dès le " + formatter.date(it) }
                    ?: MOVE_IN_LABELS[mandate.movingPeriod] ?: NONE
                ),
            "Catégorie" to (category(mandate.propertyCategory) ?: NONE),
            "Type de propriété" to (PROPERTY_TYPE_LABELS[mandate.propertyType] ?: "Indifférent"),
            ("Meublé" to furniture(mandate.furnitureType))
                .takeIf { !sale && FindPropertyRules.needsFurniture(type, mandate.listingType) },
            mandate.rooms?.let { "Chambres" to it }
                ?.takeIf { FindPropertyRules.needsRooms(mandate.propertyCategory, type, mandate.listingType) },
            ("Parking" to (mandate.parkings?.let { PARKING_VALUES[it] ?: it } ?: "Indifférent"))
                .takeIf { FindPropertyRules.asksParkings(type) },
            mandate.area?.let { (if (owner) "Surface" else "Surface souhaitée") to formatNumber(it) + " m²" },
        ) + listOfNotNull(
            mandate.financingType?.takeIf { sale }?.let { "Financement" to (FINANCING_LABELS[it] ?: NONE) },
            ("Documents exigés" to titleDocuments(mandate.titleDocuments))
                .takeIf { sale && !owner && type == PropertyType.LAND },
            ("Document de propriété" to ownershipDocuments(mandate.ownershipDocuments))
                .takeIf { sale && owner && type == PropertyType.LAND && mandate.ownershipDocuments.isNotEmpty() },
        ) + amenityGroups(mandate) + listOfNotNull(mandate.comments?.trim()?.ifEmpty { null }
            ?.let { "Autres précisions" to it })
    }

    private const val NONE = "—"
}
