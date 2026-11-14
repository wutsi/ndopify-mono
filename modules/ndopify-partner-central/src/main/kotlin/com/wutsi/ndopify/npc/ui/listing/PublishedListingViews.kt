package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.npc.ui.mandate.Amenity
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.util.Locale

/**
 * Builds the published listing sheet from the listing itself, its images and the location names. It never sees the
 * match, nor the listing agent: the audience is a single [of] flag.
 */
object PublishedListingViews {
    private const val CO_BROKER_NOTE = "L'adresse exacte et l'itinéraire sont communiqués par l'agent avant la visite."

    /**
     * [images] is null when they couldn't be loaded: no photo then. [owner] says whether the private location is built;
     * a co-broker gets the shared note instead.
     */
    fun of(
        listing: Listing,
        images: List<ListingImage>?,
        locations: Map<Long, Location>,
        formatter: TenantFormatter,
        owner: Boolean,
    ): PublishedListingView =
        PublishedListingView(
            description = listing.description,
            photos = photos(listing, images),
            facts = facts(listing, formatter),
            property = property(listing, formatter),
            place = PlaceView(
                neighbourhood = listing.neighborhoodId?.let { locations[it]?.name },
                city = locations[listing.cityId]?.name,
                landmark = listing.landmark,
            ),
            placeNote = if (owner) null else CO_BROKER_NOTE,
            amenities = amenities(listing),
            terms = terms(listing, formatter),
            remarks = listing.agentRemarks,
            privateLocation = if (owner) privateLocation(listing) else null,
        )

    // The hero first, then the others in image order; a refused or blank image never shows.
    private fun photos(listing: Listing, images: List<ListingImage>?): List<GalleryPhotoView> {
        val good = images.orEmpty().filter { it.status != ImageStatus.REJECTED && it.url.isNotBlank() }
        val hero = good.firstOrNull { it.id == listing.heroImageId }
        val ordered = listOfNotNull(hero) + good.filter { it !== hero }
        return ordered.mapIndexed { index, image ->
            GalleryPhotoView(
                url = image.url,
                alt = image.title?.trim()?.ifEmpty { null }
                    ?: image.description?.trim()?.ifEmpty { null }
                    ?: "Photo ${index + 1}",
            )
        }
    }

    private fun facts(listing: Listing, formatter: TenantFormatter): List<FactView> = listOfNotNull(
        listing.rooms?.let { FactView("bed", plural(it, "chambre", "chambres")) },
        listing.bathrooms?.let { FactView("bath", plural(it, "salle de bains", "salles de bains")) },
        listing.area?.let { FactView("area", "${formatter.count(it.toLong())} m²") },
        if (listing.furnitureType == FurnitureType.FULLY_FURNISHED) FactView("furnished", "Meublé") else null,
        listing.parkings?.let { FactView("parking", plural(it, "parking", "parkings")) },
    )

    private fun property(listing: Listing, formatter: TenantFormatter): List<SpecView> = listOfNotNull(
        listing.propertyType?.let { type -> MandateLabels.PROPERTY_TYPE_LABELS[type]?.let { SpecView("Type de bien", it) } },
        listing.furnitureType?.let { type -> MandateLabels.FURNITURE_TYPE_LABELS[type]?.let { SpecView("Ameublement", it) } },
        listing.rooms?.let { SpecView("Chambres", it.toString()) },
        listing.bathrooms?.let { SpecView("Salles de bains", it.toString()) },
        listing.livingRooms?.let { SpecView("Salons", it.toString()) },
        listing.kitchens?.let { SpecView("Cuisines", it.toString()) },
        listing.area?.let { SpecView("Surface", "${formatter.count(it.toLong())} m²") },
        listing.parkings?.let { SpecView("Parkings", it.toString()) },
    )

    private fun amenities(listing: Listing): List<AmenityGroupView> =
        Amenity.of(listing)
            .groupBy { it.group }
            .map { (group, items) -> AmenityGroupView(group, items.map { it.label }) }

    private fun terms(listing: Listing, formatter: TenantFormatter): List<SpecView> {
        val money = { months: Int -> formatter.money(months.toLong() * listing.price, listing.currencyCode) }
        return listOfNotNull(
            if (listing.price > 0) {
                SpecView(
                    "Loyer",
                    MandateLabels.price(
                        Money(listing.price, listing.currencyCode, listing.pricePeriodicityType),
                        formatter,
                    ),
                )
            } else {
                null
            },
            listing.securityDeposit?.let {
                SpecView("Caution", if (it > 0) "${plural(it, "mois", "mois")} (${money(it)})" else "Sans caution")
            },
            listing.advanceRent?.let {
                SpecView("Avance sur loyer", if (it > 0) "${plural(it, "mois", "mois")} (${money(it)})" else "Sans avance")
            },
            listing.availableFrom?.let { SpecView("Disponible", "À partir du ${formatter.date(it)}") },
            listing.leaseTerm?.let { SpecView("Durée du bail", plural(it, "mois", "mois")) },
            listing.noticePeriod?.let { SpecView("Préavis", plural(it, "mois", "mois")) },
        )
    }

    private fun privateLocation(listing: Listing): PrivateLocationView? {
        val latitude = listing.latitude
        val longitude = listing.longitude
        val hasGps = latitude != null && longitude != null
        val directions = listing.accessDirections?.trim()?.ifEmpty { null }
        if (!hasGps && directions == null) return null

        return PrivateLocationView(
            coordinates = if (hasGps) String.format(Locale.US, "%.5f, %.5f", latitude, longitude) else null,
            mapsUrl = if (hasGps) "https://maps.google.com/?q=$latitude,$longitude" else null,
            accessDirections = directions,
        )
    }

    private fun plural(n: Int, one: String, many: String): String = "$n ${if (n > 1) many else one}"
}
