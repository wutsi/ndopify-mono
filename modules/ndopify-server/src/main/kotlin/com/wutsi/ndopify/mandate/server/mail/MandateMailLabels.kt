package com.wutsi.ndopify.mandate.server.mail

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.FinancingType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter

/** How the mandate e-mails word a mandate's criteria, shared by [MandateOfferMailet] and [MandateApprovedMailet]. */
internal object MandateMailLabels {
    private val PROPERTY_TYPES = mapOf(
        PropertyType.ROOM to "Chambre",
        PropertyType.STUDIO to "Studio",
        PropertyType.APARTMENT to "Appartement",
        PropertyType.VILLA to "Villa",
        PropertyType.DUPLEX to "Duplex",
        PropertyType.HOUSE to "Maison",
        PropertyType.BUILDING to "Immeuble",
        PropertyType.LAND to "Terrain",
        PropertyType.RETAIL to "Local commercial",
        PropertyType.INDUSTRIAL to "Local industriel",
        PropertyType.OFFICE to "Bureau",
    )

    private val PERIODICITIES = mapOf(
        PeriodicityType.DAILY to "jour",
        PeriodicityType.WEEKLY to "semaine",
        PeriodicityType.MONTHLY to "mois",
        PeriodicityType.QUARTERLY to "trimestre",
        PeriodicityType.YEARLY to "an",
    )

    // How a purchase is financed (FinancingType): a buyer's declaration, shown to the agent.
    private val FINANCING_TYPES = mapOf(
        FinancingType.CASH to "Comptant",
        FinancingType.BANK_LOAN to "Prêt bancaire",
        FinancingType.TRANSFER_FROM_ABROAD to "Transfert depuis l'étranger",
        FinancingType.UNDECIDED to "Pas encore décidé",
    )

    // The land documents a buyer requires (TitleDocumentType).
    private val TITLE_DOCUMENTS = mapOf(
        TitleDocumentType.LAND_TITLE to "Titre foncier",
        TitleDocumentType.SURVEY_PLAN to "Plan de bornage",
        TitleDocumentType.CERTIFICATE_OF_OWNERSHIP to "Certificat de propriété",
    )

    // A customer searching buys, an owner listing sells
    fun listingType(mandate: MandateEntity): String =
        when {
            mandate.listingType != ListingType.SALE -> "Location"
            mandate.mandateType == MandateType.LISTING -> "Vente"
            else -> "Achat"
        }

    // Whether the mandate is a sale: its e-mail then words the buyer's or the seller's flow. A rental's copy never
    // changes, so a mailet that doesn't set it keeps the rental wording.
    fun sale(mandate: MandateEntity): Boolean =
        mandate.listingType == ListingType.SALE

    // The label of a property's amount in an e-mail (the `price_label` context key, a listing's amount): "Prix" for a
    // sale, "Loyer" for a rental. Not to be confused with [amountLabel]: this one words a property, that one names a
    // sale *mandate's* amount to its agent, so a SALE SEARCH is a "Budget" where this one would say "Prix".
    fun priceLabel(mandate: MandateEntity): String =
        if (mandate.listingType == ListingType.SALE) "Prix" else "Loyer"

    // The amount's label in a sale mandate's Telegram message: the seller's listing is a "Prix", the buyer's search a
    // "Budget" (see [priceLabel] for the e-mail's property label). Used by [amount], only on a sale: a rental's
    // message keeps its bare amount.
    fun amountLabel(mandate: MandateEntity): String =
        if (mandate.mandateType == MandateType.LISTING) "Prix" else "Budget"

    // What ends a bot message's property line: " — 150 000 XAF / mois" for a rental, and
    // " — Budget : 250 000 000 XAF" (a search) / " — Prix : …" (a listing) for a sale.
    fun amount(mandate: MandateEntity, formatter: TenantFormatter): String {
        val budget = budget(mandate, formatter)
        return if (sale(mandate)) " — ${amountLabel(mandate)} : $budget" else " — $budget"
    }

    fun propertyType(mandate: MandateEntity): String? =
        mandate.propertyType?.let { PROPERTY_TYPES[it] }

    // Ex: "Comptant"; null for a rental, and for no usable answer.
    fun financingType(mandate: MandateEntity): String? =
        if (mandate.listingType == ListingType.SALE) mandate.financingType?.let { FINANCING_TYPES[it] } else null

    // Ex: "Titre foncier · Plan de bornage"; null without documents. "·", never a comma: a document name may contain one.
    fun titleDocuments(mandate: MandateEntity): String? =
        mandate.titleDocuments
            .mapNotNull { TITLE_DOCUMENTS[it] }
            .joinToString(" · ")
            .ifEmpty { null }

    // Ex: "Titre foncier · Plan de bornage"; null without documents, and outside a land sale (a rental, another type).
    // "·", never a comma: a document name may contain one.
    fun ownershipDocuments(mandate: MandateEntity): String? =
        if (mandate.listingType == ListingType.SALE && mandate.propertyType == PropertyType.LAND) {
            mandate.ownershipDocuments
                .mapNotNull { TITLE_DOCUMENTS[it] }
                .joinToString(" · ")
                .ifEmpty { null }
        } else {
            null
        }

    fun neighbourhoods(mandate: MandateEntity, locationService: LocationService): String? =
        mandate.neighborhoodIds
            .mapNotNull { id -> locationService.findByIdOrNull(id)?.name }
            .joinToString(", ")
            .ifEmpty { null }

    // Where the mandate is, for a subject. Ex: "Bastos, Mvan (Yaounde)", or "Yaounde" without neighbourhoods
    fun place(mandate: MandateEntity, city: String, locationService: LocationService): String {
        val neighbourhoods = neighbourhoods(mandate, locationService) ?: return city
        return if (city.isEmpty()) neighbourhoods else "$neighbourhoods ($city)"
    }

    // Ex: 150 000 FCFA / mois
    fun budget(mandate: MandateEntity, formatter: TenantFormatter): String =
        price(mandate.price, mandate.currencyCode, mandate.pricePeriodicityType, formatter)

    // Ex: 150 000 FCFA / mois, or 25 000 000 FCFA for a sale
    fun price(amount: Long, currencyCode: String, periodicity: PeriodicityType?, formatter: TenantFormatter): String {
        val suffix = PERIODICITIES[periodicity]?.let { " / $it" } ?: ""
        return "${formatter.money(amount, currencyCode)}$suffix"
    }
}
