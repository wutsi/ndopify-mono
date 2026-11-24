package com.wutsi.ndopify.web.ui.listing

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.time.LocalDate

/**
 * Builds a listing's [ListingPage] (the prototype `annonce.html`) from ndopify-server's DTOs. Only the listing's
 * public fields are used: never its `agentRemarks`, private to its agent.
 *
 * Without generated content (the publisher gave up), the title is "<type> à louer à <place>" and the description is
 * left out. The amounts next to the deposit and the advance are only given for a monthly price, which they're months
 * of. The visit popup's days go from tomorrow to tomorrow + 7 days in the tenant's time zone ([today] is its date),
 * its times from 10:00 to 21:00 every 30 minutes.
 *
 * A SALE listing's conditions are the price and the availability only, whatever rental terms were saved before: never a
 * rent, a deposit, an advance, a lease term, a notice period nor the furniture.
 *
 * A building sold as a whole reads its unités, its étages and its surface instead of its rooms, its bathrooms and its
 * parking, and shows no verified amenity since a building sale asks none.
 */
object ListingPages {
    internal val PROPERTY_TYPE_LABELS: Map<PropertyType, String> = mapOf(
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

    internal val FURNITURE_LABELS = mapOf(
        FurnitureType.FULLY_FURNISHED to "Meublé",
        FurnitureType.UNFURNISHED to "Non meublé",
    )

    private val SCHEMA_TYPES = mapOf(
        PropertyType.APARTMENT to "Apartment",
        PropertyType.STUDIO to "Apartment",
        PropertyType.HOUSE to "House",
        PropertyType.VILLA to "House",
        PropertyType.DUPLEX to "House",
        PropertyType.ROOM to "Room",
    )

    internal val PERIODS = mapOf(
        PeriodicityType.MONTHLY to "/ mois",
        PeriodicityType.YEARLY to "/ an",
        PeriodicityType.WEEKLY to "/ semaine",
        PeriodicityType.DAILY to "/ jour",
    )

    /** The prototype's groups and wording; a dedicated meter replaces the plain one. */
    private val AMENITIES: List<Pair<String, List<Pair<String, (Listing) -> Boolean>>>> = listOf(
        "Eau" to listOf(
            "Compteur d'eau dédié" to { l: Listing -> l.dedicatedWaterMeter },
            "Compteur d'eau" to { l: Listing -> l.waterMeter && !l.dedicatedWaterMeter },
            "Réservoir d'eau" to { l: Listing -> l.waterTank },
            "Forage" to { l: Listing -> l.waterWell },
        ),
        "Électricité" to listOf(
            "Compteur électrique dédié" to { l: Listing -> l.dedicatedElectricityMeter },
            "Compteur électrique" to { l: Listing -> l.electricityMeter && !l.dedicatedElectricityMeter },
            "Groupe électrogène" to { l: Listing -> l.generatorSet },
            "Panneaux solaires" to { l: Listing -> l.solarPanel },
        ),
        "Accès" to listOf(
            "Route bitumée" to { l: Listing -> l.roadPaved },
            "Accessible en voiture" to { l: Listing -> l.trackAccessibleByCar },
            "Piste accessible à pied seulement" to { l: Listing -> l.trackInaccessibleByCar },
        ),
        "Sécurité" to listOf(
            "Clôturé et sécurisé" to { l: Listing -> l.securedFenced },
            "Gardien" to { l: Listing -> l.securityGuard },
            "Vidéosurveillance" to { l: Listing -> l.videoSurveillance },
        ),
    )

    fun of(
        listing: Listing,
        images: List<ListingImage>,
        locations: List<Location>,
        tenant: Tenant,
        formatter: TenantFormatter,
        today: LocalDate,
    ): ListingPage {
        val city = locations.find { it.id == listing.cityId }?.name
        val neighborhood = listing.neighborhoodId?.let { id -> locations.find { it.id == id }?.name }
        val title = listing.title?.ifBlank { null } ?: fallbackTitle(listing, neighborhood ?: city)
        val closedBadge = closedBadge(listing, formatter)
        val closed = closedBadge != null
        val closedNotice = closedBadge?.let { "Ce bien a été ${it.replaceFirstChar(Char::lowercase)}." }
        val priceAmount = formatter.compactMoney(listing.price, listing.currencyCode)
        val pricePeriod = PERIODS[listing.pricePeriodicityType]
        val price = listOfNotNull(priceAmount, pricePeriod).joinToString(" ")
        val available = listing.availableFrom?.takeIf { it.isAfter(today) }?.let { formatter.date(it) }
        val sale = listing.listingType == ListingType.SALE

        return ListingPage(
            id = listing.id,
            title = title,
            metaDescription = closedNotice?.let { "$it $title" } ?: (listing.summary?.ifBlank { null } ?: title),
            canonicalUrl = canonicalUrl(tenant, listing),
            typeBadge = closedBadge ?: if (listing.listingType == ListingType.SALE) "À vendre" else "À louer",
            sale = sale,
            conditionsTitle = if (sale) "Prix et conditions" else "Conditions de location",
            place = listOfNotNull(neighborhood, city).joinToString(", "),
            landmark = listing.landmark?.ifBlank { null }?.let { "près de $it" },
            verifiedAt = listing.verifiedAt?.let { formatter.date(it) },
            photos = photos(listing, images, title),
            keyFacts = keyFacts(listing, formatter, sale),
            description = paragraphs(listing.description),
            specs = specs(listing, formatter, sale),
            amenities = if (soldBuilding(listing)) {
                // A building sold as a whole asks no utility question: its page shows no amenity group.
                emptyList()
            } else {
                AMENITIES.mapNotNull { (group, items) ->
                    items.filter { (_, has) -> has(listing) }
                        .map { (label, _) -> label }
                        .ifEmpty { null }
                        ?.let { AmenityGroup(group, it) }
                }
            },
            price = price,
            priceAmount = priceAmount,
            pricePeriod = pricePeriod,
            terms = if (closed) {
                emptyList()
            } else if (sale) {
                listOf(
                    "Prix" to price,
                    "Disponible" to (available?.let { "À partir du $it" } ?: "Immédiatement"),
                )
            } else {
                listOfNotNull(
                    "Loyer" to price,
                    listing.securityDeposit?.let { "Caution" to if (it > 0) months(it, listing, formatter) else "Sans caution" },
                    listing.advanceRent?.let { "Avance sur loyer" to if (it > 0) months(it, listing, formatter) else "Sans avance" },
                    listing.leaseTerm?.let { "Durée du bail" to "$it mois" },
                    listing.noticePeriod?.let { "Préavis" to "$it mois" },
                    "Disponible" to (available?.let { "À partir du $it" } ?: "Immédiatement"),
                )
            },
            termsNote = "Les visites sont organisées par votre agent.",
            asideTerms = if (closed) {
                emptyList()
            } else if (sale) {
                listOf(available?.let { "Disponible le $it" } ?: "Disponible immédiatement")
            } else {
                listOfNotNull(
                    listing.securityDeposit?.let { if (it > 0) "Caution : $it mois" else "Sans caution" },
                    listing.advanceRent?.let { if (it > 0) "Avance : $it mois" else "Sans avance" },
                    available?.let { "Disponible le $it" } ?: "Disponible immédiatement",
                )
            },
            schemaType = SCHEMA_TYPES[listing.propertyType] ?: "Accommodation",
            area = listing.area,
            rooms = listing.rooms,
            bathrooms = listing.bathrooms,
            latitude = listing.latitude,
            longitude = listing.longitude,
            city = city,
            countryCode = tenant.countryCode,
            closedNotice = closedNotice,
        )
    }

    /** "Vendu le 12 oct. 2026" / "Loué" for a closed listing; null while it is on the market. */
    private fun closedBadge(listing: Listing, formatter: TenantFormatter): String? {
        val label = when (listing.status) {
            ListingStatus.SOLD -> "Vendu"
            ListingStatus.RENTED -> "Loué"
            else -> return null
        }
        val date = listing.closedAt?.let { formatter.date(it) }
        return label + (date?.let { " le $it" } ?: "")
    }

    private fun fallbackTitle(listing: Listing, place: String?): String {
        val type = PROPERTY_TYPE_LABELS[listing.propertyType] ?: "Logement"
        val project = if (listing.listingType == ListingType.SALE) "à vendre" else "à louer"
        return listOfNotNull("$type $project", place?.let { "à $it" }).joinToString(" ")
    }

    private fun canonicalUrl(tenant: Tenant, listing: Listing): String? {
        val host = tenant.publicPortalUrl?.ifBlank { null } ?: return null
        val path = listing.url?.ifBlank { null } ?: return null
        return host.trimEnd('/') + path
    }

    private fun photos(listing: Listing, images: List<ListingImage>, title: String): List<Photo> {
        val hero = images.find { it.id == listing.heroImageId } ?: images.firstOrNull()
        return listOfNotNull(hero).plus(images.filter { it !== hero }).map { image ->
            val base = image.variants.find { it.width == 960 } ?: image.variants.maxByOrNull { it.width }
            val full = image.variants.maxByOrNull { it.width }
            val thumb = image.variants.find { it.width == 240 } ?: base
            Photo(
                src = base?.url ?: image.url,
                alt = image.title?.ifBlank { null } ?: image.description?.ifBlank { null } ?: title,
                srcSet = image.variants.joinToString(", ") { variant -> "${variant.url} ${variant.width}w" },
                width = base?.width ?: image.width ?: 0,
                height = base?.height ?: image.height ?: 0,
                fullSrc = full?.url ?: image.url,
                thumbSrc = thumb?.url ?: image.url,
                socialUrl = image.socialUrl.ifBlank { base?.url ?: image.url },
                variants = image.variants,
            )
        }
    }

    private fun keyFacts(listing: Listing, formatter: TenantFormatter, sale: Boolean): List<Fact> =
        if (soldBuilding(listing)) {
            buildingFacts(listing, formatter)
        } else {
            listOfNotNull(
                listing.rooms?.let { Fact("bed", it.toString(), plural(it, "chambre", "chambres")) },
                listing.bathrooms?.let { Fact("bath", it.toString(), plural(it, "salle de bains", "salles de bains")) },
                listing.area?.let { Fact("area", formatter.count(it.toLong()), "m²") },
                // A sale shows no furniture: it is a rental question, whatever value was saved before.
                if (sale) null else FURNITURE_LABELS[listing.furnitureType]?.let { Fact("sofa", null, it) },
                listing.parkings?.let { Fact("car", it.toString(), plural(it, "parking", "parkings")) },
            )
        }

    /** A building sold as a whole: its unités, its étages and its surface, never its rooms nor its parking. */
    private fun buildingFacts(listing: Listing, formatter: TenantFormatter): List<Fact> = listOfNotNull(
        listing.units?.let { Fact("building", it.toString(), plural(it, "unité", "unités")) },
        listing.levels?.let { Fact("stairs", it.toString(), plural(it, "étage", "étages")) },
        listing.area?.let { Fact("area", formatter.count(it.toLong()), "m²") },
    )

    private fun specs(listing: Listing, formatter: TenantFormatter, sale: Boolean): List<Pair<String, String>> =
        if (soldBuilding(listing)) {
            buildingSpecs(listing, formatter)
        } else {
            listOfNotNull(
                PROPERTY_TYPE_LABELS[listing.propertyType]?.let { "Type de bien" to it },
                if (sale) null else FURNITURE_LABELS[listing.furnitureType]?.let { "Ameublement" to it },
                listing.rooms?.let { "Chambres" to it.toString() },
                listing.bathrooms?.let { "Salles de bains" to it.toString() },
                listing.livingRooms?.let { "Salons" to it.toString() },
                listing.kitchens?.let { "Cuisines" to it.toString() },
                listing.area?.let { "Surface" to formatter.count(it.toLong()) + " m²" },
                listing.floorLevel?.let { "Étage" to floor(it, listing.levels) },
                listing.parkings?.let { "Parkings" to it.toString() },
            )
        }

    /** A building sold as a whole: Type de bien, Unités, Étages and Surface, no bathroom, room nor parking row. */
    private fun buildingSpecs(listing: Listing, formatter: TenantFormatter): List<Pair<String, String>> = listOfNotNull(
        PROPERTY_TYPE_LABELS[listing.propertyType]?.let { "Type de bien" to it },
        listing.units?.let { "Unités" to it.toString() },
        listing.levels?.let { "Étages" to it.toString() },
        listing.area?.let { "Surface" to formatter.count(it.toLong()) + " m²" },
    )

    /** A building sold as a whole, as opposed to a rented building or any other sale. */
    private fun soldBuilding(listing: Listing): Boolean =
        listing.listingType == ListingType.SALE && listing.propertyType == PropertyType.BUILDING

    /** "Rez-de-chaussée", "1er", "2e sur 3". */
    private fun floor(level: Int, levels: Int?): String {
        if (level == 0) {
            return "Rez-de-chaussée"
        }
        val ordinal = if (level == 1) "1er" else "${level}e"
        return levels?.let { "$ordinal sur $it" } ?: ordinal
    }

    /** "2 mois (350K FCFA)" for a monthly price, "2 mois" otherwise. */
    private fun months(count: Int, listing: Listing, formatter: TenantFormatter): String =
        if (listing.pricePeriodicityType == PeriodicityType.MONTHLY) {
            "$count mois (${formatter.compactMoney(listing.price * count, listing.currencyCode)})"
        } else {
            "$count mois"
        }

    private fun paragraphs(text: String?): List<String> =
        text?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun plural(count: Int, one: String, many: String): String = if (count > 1) many else one
}
