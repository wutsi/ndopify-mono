package com.wutsi.ndopify.mandate.server.mail

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateType
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

    // A customer searching buys, an owner listing sells
    fun listingType(mandate: MandateEntity): String =
        when {
            mandate.listingType != ListingType.SALE -> "Location"
            mandate.mandateType == MandateType.LISTING -> "Vente"
            else -> "Achat"
        }

    // A listing's price: its rent, or its sale price
    fun priceLabel(mandate: MandateEntity): String =
        if (mandate.listingType == ListingType.SALE) "Prix" else "Loyer"

    fun propertyType(mandate: MandateEntity): String? =
        mandate.propertyType?.let { PROPERTY_TYPES[it] }

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
