package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.service.TenantFormatter
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.util.Locale

/**
 * Builds the checklist page's [ListingView] (the prototype's `sectionSummary`, `sectionState` and `missingSections`).
 * Whether a section is done is ndopify-server's checklist ([ListingRules.done]); the photos only add the counts.
 */
object ListingViews {
    private val ROAD_ACCESS = ListingRules.ROAD_ACCESS

    // The prototype's LISTING_FURNITURE_LABELS
    private val FURNITURE_LABELS = mapOf(
        FurnitureType.FULLY_FURNISHED to "Meublé",
        FurnitureType.UNFURNISHED to "Non meublé",
    )

    private val VERIFIED = ListingRules.Tag("Vérifié", "success")
    private val TO_VERIFY = ListingRules.Tag("À vérifier", "warning")
    private val TO_COMPLETE = ListingRules.Tag("À compléter", "warning")
    private val REMARKS_ADDED = ListingRules.Tag("Ajoutées", "success")
    private val OPTIONAL = ListingRules.Tag("Facultatif", "neutral")

    /** [images] is null when they couldn't be loaded: the photos' row then has no count. */
    fun of(
        listing: Listing,
        images: List<ListingImage>?,
        locations: Map<Long, Location>,
        formatter: TenantFormatter,
    ): ListingView {
        val editable = ListingRules.editable(listing)
        val required = ListingRules.SECTIONS.filter { ListingRules.required(listing, it) }
        val done = required.count { ListingRules.done(listing, it) }
        val left = required.filter { !ListingRules.done(listing, it) }.map { missing(it, images) }
        val published = listing.status == ListingStatus.PUBLISHED
        val neighborhood = listing.neighborhoodId?.let { locations[it]?.name }

        return ListingView(
            id = listing.id,
            mandateId = listing.mandateId,
            title = title(listing, neighborhood ?: locations[listing.cityId]?.name),
            tag = ListingRules.tag(listing.status),
            status = listing.status,
            createdAt = formatter.date(listing.createdAt),
            panel = when {
                editable -> ListingView.PANEL_DRAFT
                ListingRules.sent(listing.status) -> ListingView.PANEL_SENT
                published -> ListingView.PANEL_PUBLISHED
                else -> null
            },
            // No "sent at" on the server: nothing changes a listing while it's checked, so its last change is it
            sentAt = if (ListingRules.sent(listing.status)) formatter.date(listing.modifiedAt) else null,
            publishedAt = listing.publishedAt?.let { formatter.date(it) },
            generatedTitle = if (published) listing.title else null,
            generatedSummary = if (published) listing.summary else null,
            rows = ListingRules.SECTIONS.map { section ->
                ChecklistRow(
                    section = section,
                    label = ListingRules.LABELS.getValue(section),
                    url = "/listings/${listing.id}/${ListingRules.slug(section)}",
                    summary = summary(listing, section, neighborhood, images, formatter),
                    state = state(listing, section),
                )
            },
            progress = "$done sections sur ${required.size} vérifiées",
            missing = if (left.isEmpty()) {
                "Tout est vérifié : vous pouvez envoyer l'annonce."
            } else {
                "Encore à faire : ${left.joinToString(", ")}."
            },
            editable = editable,
            sendable = editable && left.isEmpty(),
        )
    }

    // The prototype's labels of *Le bien*'s types (listing/bien.html), not the mandates'
    private val TYPE_LABELS = mapOf(
        PropertyType.APARTMENT to "Appartement",
        PropertyType.HOUSE to "Maison",
        PropertyType.VILLA to "Villa",
        PropertyType.DUPLEX to "Duplex",
        PropertyType.STUDIO to "Studio",
        PropertyType.ROOM to "Chambre",
        PropertyType.BUILDING to "Immeuble entier",
        PropertyType.OFFICE to "Bureau",
        PropertyType.RETAIL to "Boutique, local commercial",
        PropertyType.INDUSTRIAL to "Entrepôt, local industriel",
        PropertyType.LAND to "Terrain",
    )

    /** The checkboxes' labels of *Eau, électricité, accès* (listing/equipements.html, not the mandates' wording). */
    val AMENITY_LABELS: Map<String, String> = mapOf(
        "waterMeter" to "Compteur d'eau",
        "dedicatedWaterMeter" to "Compteur d'eau individuel",
        "waterWell" to "Forage",
        "waterTank" to "Château d'eau, citerne",
        "electricityMeter" to "Compteur électrique",
        "dedicatedElectricityMeter" to "Compteur électrique individuel",
        "generatorSet" to "Groupe électrogène",
        "solarPanel" to "Panneaux solaires",
        "videoSurveillance" to "Vidéosurveillance",
        "securityGuard" to "Gardien 24\u00a0h/24",
        "securedFenced" to "Barrière, clôture sécurisée",
    )

    /** *Le bien*'s types, residential first, in the prototype's order. */
    val TYPES: List<TypeChoice> = ListingRules.TYPES.flatMap { (category, types) ->
        types.map { type -> TypeChoice(type.name, category.name, TYPE_LABELS.getValue(type)) }
    }

    /** A section's page: its title, the listing it's of, and, once sent, why it's read-only. */
    fun section(
        listing: Listing,
        section: ListingSection,
        locations: Map<Long, Location>,
        formatter: TenantFormatter,
    ): SectionView {
        val title = title(listing, listing.neighborhoodId?.let { locations[it]?.name } ?: locations[listing.cityId]?.name)
        val heading = ListingRules.LABELS.getValue(section)
        val editable = ListingRules.editable(listing)
        val since = when {
            editable -> null
            listing.status == ListingStatus.PUBLISHED ->
                "Annonce publiée" + (listing.publishedAt?.let { " le ${formatter.date(it)}" } ?: "")
            ListingRules.sent(listing.status) -> "Annonce en vérification depuis le ${formatter.date(listing.modifiedAt)}"
            else -> "Annonce fermée"
        }
        return SectionView(
            listingId = listing.id,
            mandateId = listing.mandateId,
            slug = ListingRules.slug(section),
            heading = heading,
            meta = "$title · mandat n° ${listing.mandateId}",
            pageTitle = "$heading · $title — Ndopify Partner Central",
            editable = editable,
            readonlyText = since?.let { "$it : voici ce que vous avez relevé, il ne peut plus être modifié." },
        )
    }

    /**
     * "Indiqué par le propriétaire : …" under each field whose answer isn't the landlord's (the prototype's
     * `syncOwnerHints`), by field ("amenities" for the amenities together); none for what the landlord didn't answer,
     * nor without [mandate].
     */
    fun ownerHints(
        form: ListingSectionForm,
        mandate: Mandate?,
        locations: Map<Long, Location>,
        formatter: TenantFormatter,
    ): Map<String, String> {
        mandate ?: return emptyMap()
        val owner = linkedMapOf<String, String?>()

        mandate.neighborhoodIds.firstOrNull()?.let { id ->
            if (form.neighborhoodId?.trim()?.toLongOrNull() != id) owner["neighborhoodId"] = locations[id]?.name
        }
        mandate.propertyCategory.takeIf { it != PropertyCategory.UNKNOWN }?.let { category ->
            if (form.propertyCategory != category.name) owner["propertyCategory"] = MandateLabels.category(category)
        }
        mandate.propertyType?.let { type ->
            if (form.propertyType != type.name) owner["propertyType"] = MandateLabels.PROPERTY_TYPE_LABELS[type]
        }
        mandate.furnitureType?.takeIf { it != FurnitureType.UNKNOWN }?.let { furniture ->
            if (form.furnitureType != furniture.name) owner["furnitureType"] = FURNITURE_LABELS[furniture]
        }
        // "2", or "3+" for 3 or more: shown as the landlord answered it
        mapOf("rooms" to (mandate.rooms to form.rooms), "parkings" to (mandate.parkings to form.parkings))
            .forEach { (field, values) ->
                val (answer, value) = values
                val count = answer?.trim()?.removeSuffix("+")?.toIntOrNull() ?: return@forEach
                if (ListingRules.count(value) != count) owner[field] = answer.trim()
            }
        mandate.area?.let { area ->
            if (ListingRules.count(form.area) != area) owner["area"] = "${formatter.count(area.toLong())} m²"
        }
        val amenities = amenities(mandate)
        if (amenities != form.amenities()) {
            owner["amenities"] = MandateLabels.AMENITY_LABELS.filterKeys { it in amenities }.values
                .joinToString(", ")
                .ifEmpty { "aucun équipement" }
        }
        mandate.price.amount.takeIf { it > 0 }?.let { amount ->
            if (form.price?.let { ListingRules.amount(it) } != amount) owner["price"] = MandateLabels.rent(mandate.price, formatter)
        }
        mapOf(
            "securityDeposit" to (mandate.securityDeposit to form.securityDeposit),
            "advanceRent" to (mandate.advanceRent to form.advanceRent),
        ).forEach { (field, values) ->
            val (months, value) = values
            if (months != null && ListingRules.count(value) != months) owner[field] = "$months mois"
        }
        mandate.availableFrom?.let { date ->
            if (form.availableFrom?.trim() != date.toString()) owner["availableFrom"] = formatter.date(date)
        }

        return owner.mapNotNull { (field, text) -> text?.let { field to "Indiqué par le propriétaire : $it" } }.toMap()
    }

    /**
     * The listing's line on the page of [mandate] (the prototype's `renderListing`); null for a mandate that has none
     * to have: a search, or a landlord's not signed yet. [listing] is null before it's created, or when it can't be
     * loaded (only the link is then shown). The photos aren't loaded here: what's left names the section only.
     */
    fun forMandate(mandate: Mandate, listing: Listing?, formatter: TenantFormatter): MandateListingView? {
        if (mandate.mandateType != MandateType.LISTING || mandate.status != MandateStatus.APPROVED) return null

        val createUrl = "/mandates/${mandate.id}/listing"
        val listingId = mandate.listingId
            ?: return MandateListingView(
                url = null,
                createUrl = createUrl,
                text = "Visitez le bien pour vérifier ses caractéristiques et prendre les photos : " +
                    "l'annonce est publiée une fois vérifiée.",
                action = "Créer l'annonce",
                primary = true,
            )

        val url = "/listings/$listingId"
        val draft = listing != null && ListingRules.editable(listing)
        val text = when {
            listing == null -> null
            draft -> {
                val left = ListingRules.SECTIONS
                    .filter { ListingRules.required(listing, it) && !ListingRules.done(listing, it) }
                    .map { ListingRules.LABELS.getValue(it) }
                "Annonce en brouillon : " +
                    if (left.isEmpty()) "prête à être envoyée." else "encore à faire, ${left.joinToString(", ")}."
            }
            ListingRules.sent(listing.status) -> "Annonce envoyée le ${formatter.date(listing.modifiedAt)}, en vérification."
            listing.status == ListingStatus.PUBLISHED ->
                listing.publishedAt?.let { "Annonce publiée le ${formatter.date(it)}." } ?: "Annonce publiée."
            else -> null
        }
        return MandateListingView(
            url = url,
            createUrl = createUrl,
            text = text,
            action = if (draft) "Continuer l'annonce" else "Voir l'annonce",
            primary = draft,
        )
    }

    private fun title(listing: Listing, place: String?): String {
        val type = MandateLabels.shortPropertyType(listing.propertyType) ?: "Bien"
        return "Annonce · " + listOfNotNull(type, place).joinToString(", ")
    }

    // "Emplacement", or what the photos lack: "la photo refusée, 3 photos"
    private fun missing(section: ListingSection, images: List<ListingImage>?): String {
        if (section != ListingSection.PHOTOS || images == null) return ListingRules.LABELS.getValue(section)

        val good = good(images)
        val refused = images.size - good
        val short = ListingRules.MIN_PHOTOS - good
        return listOfNotNull(
            when {
                refused > 1 -> "les photos refusées"
                refused == 1 -> "la photo refusée"
                else -> null
            },
            if (short > 0) plural(short, "photo", "photos") else null,
        ).joinToString(", ").ifEmpty { ListingRules.LABELS.getValue(section) }
    }

    private fun summary(
        listing: Listing,
        section: ListingSection,
        neighborhood: String?,
        images: List<ListingImage>?,
        formatter: TenantFormatter,
    ): String = when (section) {
        ListingSection.LOCATION ->
            listOfNotNull(neighborhood, listing.landmark ?: "Point de repère à noter").joinToString(" · ")

        ListingSection.GPS ->
            if (listing.latitude != null && listing.longitude != null) {
                String.format(Locale.US, "%.5f, %.5f", listing.latitude, listing.longitude)
            } else {
                "À relever devant l'entrée du bien"
            }

        ListingSection.PROPERTY -> listOfNotNull(
            listing.propertyType?.let { MandateLabels.PROPERTY_TYPE_LABELS[it] },
            listing.rooms?.let { plural(it, "chambre", "chambres") },
            listing.area?.let { "${formatter.count(it.toLong())} m²" },
        ).joinToString(" · ")

        ListingSection.AMENITIES -> {
            val checked = amenities(listing).filterValues { it }.keys
            val road = checked.firstOrNull { it in ROAD_ACCESS }
            val others = checked.count { it !in ROAD_ACCESS }
            listOf(
                road?.let { MandateLabels.AMENITY_LABELS[it] } ?: "Accès à préciser",
                if (others > 0) plural(others, "équipement", "équipements") else "aucun équipement",
            ).joinToString(" · ")
        }

        ListingSection.TERMS -> {
            val rent = MandateLabels.compactRent(
                Money(listing.price, listing.currencyCode, PeriodicityType.MONTHLY),
                formatter,
            )
            rent + (listing.securityDeposit?.let { " · caution $it mois" } ?: "")
        }

        ListingSection.PHOTOS -> images?.let {
            val good = good(it)
            (if (good > 0) plural(good, "photo", "photos") else "Aucune photo") +
                (if (good < ListingRules.MIN_PHOTOS) " · ${ListingRules.MIN_PHOTOS} au moins" else "")
        } ?: ""

        else -> listing.agentRemarks ?: "Aucune remarque"
    }

    private fun state(listing: Listing, section: ListingSection): ListingRules.Tag = when {
        section == ListingSection.REMARKS && !ListingRules.required(listing, section) ->
            if (listing.agentRemarks != null) REMARKS_ADDED else OPTIONAL
        ListingRules.done(listing, section) -> VERIFIED
        section == ListingSection.PHOTOS -> TO_COMPLETE
        else -> TO_VERIFY
    }

    // A photo refused by the check is deleted (or replaced) before sending again
    private fun good(images: List<ListingImage>): Int = images.count { it.status != ImageStatus.REJECTED }

    // Same keys and order as MandateLabels.AMENITY_LABELS
    private fun amenities(listing: Listing): Map<String, Boolean> = linkedMapOf(
        "waterMeter" to listing.waterMeter,
        "dedicatedWaterMeter" to listing.dedicatedWaterMeter,
        "waterTank" to listing.waterTank,
        "waterWell" to listing.waterWell,
        "electricityMeter" to listing.electricityMeter,
        "dedicatedElectricityMeter" to listing.dedicatedElectricityMeter,
        "generatorSet" to listing.generatorSet,
        "solarPanel" to listing.solarPanel,
        "roadPaved" to listing.roadPaved,
        "trackAccessibleByCar" to listing.trackAccessibleByCar,
        "trackInaccessibleByCar" to listing.trackInaccessibleByCar,
        "securedFenced" to listing.securedFenced,
        "securityGuard" to listing.securityGuard,
        "videoSurveillance" to listing.videoSurveillance,
    )

    private fun amenities(mandate: Mandate): Set<String> = linkedMapOf(
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
    ).filterValues { it }.keys

    private fun plural(n: Int, one: String, many: String): String = "$n ${if (n > 1) many else one}"
}
