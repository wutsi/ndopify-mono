package com.wutsi.ndopify.mandate.server.mail

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
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

    fun listingType(mandate: MandateEntity): String =
        if (mandate.listingType == ListingType.SALE) "Achat" else "Location"

    fun propertyType(mandate: MandateEntity): String? =
        mandate.propertyType?.let { PROPERTY_TYPES[it] }

    fun neighbourhoods(mandate: MandateEntity, locationService: LocationService): String? =
        mandate.neighborhoodIds
            .mapNotNull { id -> locationService.findByIdOrNull(id)?.name }
            .joinToString(", ")
            .ifEmpty { null }

    // Ex: 150 000 FCFA / mois
    fun budget(mandate: MandateEntity, formatter: TenantFormatter): String {
        val amount = formatter.money(mandate.price, mandate.currencyCode)
        val periodicity = PERIODICITIES[mandate.pricePeriodicityType]?.let { " / $it" } ?: ""
        return "$amount$periodicity"
    }
}
