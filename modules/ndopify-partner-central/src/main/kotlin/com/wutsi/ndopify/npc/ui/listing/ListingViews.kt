package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingStatus
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
    private val ROAD_ACCESS = listOf("roadPaved", "trackAccessibleByCar", "trackInaccessibleByCar")

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
        val required = ListingRules.SECTIONS.filter { it !in ListingRules.OPTIONAL_SECTIONS }
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
            listing.area?.let { "${MandateLabels.formatNumber(it)} m²" },
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
        section == ListingSection.REMARKS -> if (listing.agentRemarks != null) REMARKS_ADDED else OPTIONAL
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

    private fun plural(n: Int, one: String, many: String): String = "$n ${if (n > 1) many else one}"
}
