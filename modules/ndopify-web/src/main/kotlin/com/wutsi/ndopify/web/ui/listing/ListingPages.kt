package com.wutsi.ndopify.web.ui.listing

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.time.LocalDate
import java.time.LocalTime

/**
 * Builds a listing's [ListingPage] (the prototype `annonce.html`) from ndopify-server's DTOs. Only the listing's
 * public fields are used: never its `agentRemarks`, private to its agent.
 *
 * Without generated content (the publisher gave up), the title is "<type> à louer à <place>" and the description is
 * left out. The amounts next to the deposit and the advance are only given for a monthly price, which they're months
 * of. The visit popup's days go from tomorrow to tomorrow + 7 days in the tenant's time zone ([today] is its date),
 * its times from 10:00 to 21:00 every 30 minutes.
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

    private const val VISIT_DAYS = 8L
    private val FIRST_VISIT_TIME = LocalTime.of(10, 0)
    private val LAST_VISIT_TIME = LocalTime.of(21, 0)

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
        val priceAmount = formatter.compactMoney(listing.price, listing.currencyCode)
        val pricePeriod = PERIODS[listing.pricePeriodicityType]
        val price = listOfNotNull(priceAmount, pricePeriod).joinToString(" ")
        val available = listing.availableFrom?.takeIf { it.isAfter(today) }?.let { formatter.date(it) }

        return ListingPage(
            id = listing.id,
            title = title,
            metaDescription = listing.summary?.ifBlank { null } ?: title,
            canonicalUrl = canonicalUrl(tenant, listing),
            typeBadge = if (listing.listingType == ListingType.SALE) "À vendre" else "À louer",
            place = listOfNotNull(neighborhood, city).joinToString(", "),
            landmark = listing.landmark?.ifBlank { null }?.let { "près de $it" },
            verifiedAt = listing.verifiedAt?.let { formatter.date(it) },
            photos = photos(listing, images, title),
            keyFacts = keyFacts(listing, formatter),
            description = paragraphs(listing.description),
            specs = specs(listing, formatter),
            amenities = AMENITIES.mapNotNull { (group, items) ->
                items.filter { (_, has) -> has(listing) }
                    .map { (label, _) -> label }
                    .ifEmpty { null }
                    ?.let { AmenityGroup(group, it) }
            },
            price = price,
            priceAmount = priceAmount,
            pricePeriod = pricePeriod,
            terms = listOfNotNull(
                "Loyer" to price,
                listing.securityDeposit?.let { "Caution" to months(it, listing, formatter) },
                listing.advanceRent?.let { "Avance sur loyer" to months(it, listing, formatter) },
                listing.leaseTerm?.let { "Durée du bail" to "$it mois" },
                listing.noticePeriod?.let { "Préavis" to "$it mois" },
                "Disponible" to (available?.let { "À partir du $it" } ?: "Immédiatement"),
            ),
            asideTerms = listOfNotNull(
                listing.securityDeposit?.let { "Caution : $it mois" },
                listing.advanceRent?.let { "Avance : $it mois" },
                available?.let { "Disponible le $it" } ?: "Disponible immédiatement",
            ),
            schemaType = SCHEMA_TYPES[listing.propertyType] ?: "Accommodation",
            area = listing.area,
            rooms = listing.rooms,
            bathrooms = listing.bathrooms,
            latitude = listing.latitude,
            longitude = listing.longitude,
            city = city,
            countryCode = tenant.countryCode,
            visitDays = (1..VISIT_DAYS).map { today.plusDays(it) }.map { day ->
                VisitDay(
                    value = day.toString(),
                    dayName = formatter.date(day, "EEE"),
                    day = day.dayOfMonth.toString(),
                    month = formatter.date(day, "MMM"),
                )
            },
            visitTimes = generateSequence(FIRST_VISIT_TIME) { it.plusMinutes(30) }
                .takeWhile { !it.isAfter(LAST_VISIT_TIME) }
                .map { time -> time.toString() to "%dh%02d".format(time.hour, time.minute) }
                .toList(),
        )
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
            Photo(src = image.url, alt = image.title?.ifBlank { null } ?: image.description?.ifBlank { null } ?: title)
        }
    }

    private fun keyFacts(listing: Listing, formatter: TenantFormatter): List<Fact> = listOfNotNull(
        listing.rooms?.let { Fact("bed", it.toString(), plural(it, "chambre", "chambres")) },
        listing.bathrooms?.let { Fact("bath", it.toString(), plural(it, "salle de bains", "salles de bains")) },
        listing.area?.let { Fact("area", formatter.count(it.toLong()), "m²") },
        FURNITURE_LABELS[listing.furnitureType]?.let { Fact("sofa", null, it) },
        listing.parkings?.let { Fact("car", it.toString(), plural(it, "parking", "parkings")) },
    )

    private fun specs(listing: Listing, formatter: TenantFormatter): List<Pair<String, String>> = listOfNotNull(
        PROPERTY_TYPE_LABELS[listing.propertyType]?.let { "Type de bien" to it },
        FURNITURE_LABELS[listing.furnitureType]?.let { "Ameublement" to it },
        listing.rooms?.let { "Chambres" to it.toString() },
        listing.bathrooms?.let { "Salles de bains" to it.toString() },
        listing.livingRooms?.let { "Salons" to it.toString() },
        listing.kitchens?.let { "Cuisines" to it.toString() },
        listing.area?.let { "Surface" to formatter.count(it.toLong()) + " m²" },
        listing.floorLevel?.let { "Étage" to floor(it, listing.levels) },
        listing.parkings?.let { "Parkings" to it.toString() },
    )

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
