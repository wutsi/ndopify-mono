package com.wutsi.ndopify.web.ui.match

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.core.service.TenantFormatter
import com.wutsi.ndopify.web.ui.listing.ListingPages
import java.time.LocalDate

/**
 * Builds the [MatchPage] of a proposed match (the prototype `proposition.html`) from ndopify-server's DTOs; the
 * listing's part is [ListingPages]'s, so never its `agentRemarks`.
 *
 * The state: [AnswerState.UNAVAILABLE] when the listing couldn't be read (null: a 403/404 to the customer) or isn't
 * PUBLISHED anymore, whatever the answer, since nothing can be done with it; otherwise PROPOSED → PENDING, ACCEPTED,
 * REJECTED (any other status, which ndopify-server doesn't show the customer, → UNAVAILABLE).
 *
 * "Pourquoi ce logement ?" only says what is true and was searched: one line per criterion fully met (score 1.0) that
 * the mandate sets (the neighbourhood when the listing is inside a searched one, `distanceKm == 0`), and one partial
 * line when some searched amenities are missing. The flash (`answer`: `accepted` / `rejected`, set by the answer's
 * redirect) is only shown when the match is in that state, so a hand-typed `?answer=` says nothing false. No text says
 * the agent was told of the answer: ndopify-server doesn't notify the agent yet.
 */
object MatchPages {
    /** The searched amenities, by `Match.missingAmenities` field name, worded as on the listing page. */
    private val AMENITIES: List<Triple<String, String, (Mandate) -> Boolean>> = listOf(
        Triple("waterMeter", "compteur d'eau", { m: Mandate -> m.waterMeter }),
        Triple("dedicatedWaterMeter", "compteur d'eau dédié", { m: Mandate -> m.dedicatedWaterMeter }),
        Triple("waterTank", "réservoir d'eau", { m: Mandate -> m.waterTank }),
        Triple("waterWell", "forage", { m: Mandate -> m.waterWell }),
        Triple("electricityMeter", "compteur électrique", { m: Mandate -> m.electricityMeter }),
        Triple("dedicatedElectricityMeter", "compteur électrique dédié", { m: Mandate -> m.dedicatedElectricityMeter }),
        Triple("generatorSet", "groupe électrogène", { m: Mandate -> m.generatorSet }),
        Triple("solarPanel", "panneaux solaires", { m: Mandate -> m.solarPanel }),
        Triple("roadPaved", "route bitumée", { m: Mandate -> m.roadPaved }),
        Triple("trackAccessibleByCar", "accessible en voiture", { m: Mandate -> m.trackAccessibleByCar }),
        Triple("securedFenced", "clôturé et sécurisé", { m: Mandate -> m.securedFenced }),
        Triple("securityGuard", "gardien", { m: Mandate -> m.securityGuard }),
        Triple("videoSurveillance", "vidéosurveillance", { m: Mandate -> m.videoSurveillance }),
    )

    private const val NO_AGENT_NAME = "votre agent"

    fun of(
        match: Match,
        mandate: Mandate,
        agent: Agent,
        listing: Listing?,
        images: List<ListingImage>,
        locations: List<Location>,
        tenant: Tenant,
        formatter: TenantFormatter,
        today: LocalDate,
        answer: String?,
    ): MatchPage {
        val state = state(match, listing)
        val listingPage = listing?.takeIf { state != AnswerState.UNAVAILABLE }
            ?.let { ListingPages.of(it, images, locations, tenant, formatter, today) }
        val firstName = agent.party.firstName.trim().ifEmpty { NO_AGENT_NAME }
        val fullName = "${agent.party.firstName} ${agent.party.lastName}".trim().ifEmpty { NO_AGENT_NAME }

        return MatchPage(
            id = match.id,
            state = state,
            title = "$firstName vous propose ce logement".replaceFirstChar { it.uppercase() },
            searchSummary = searchSummary(mandate, locations, tenant, formatter),
            agentFirstName = firstName,
            agentName = fullName.replaceFirstChar { it.uppercase() },
            agentInitials = listOf(agent.party.firstName, agent.party.lastName)
                .mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }
                .joinToString(""),
            agentWhatsappUrl = agent.whatsappNumber?.filter { it.isDigit() }?.ifEmpty { null }?.let { "https://wa.me/$it" },
            reasons = if (listing != null && listingPage != null) {
                reasons(match, mandate, listing, listingPage.price, locations, formatter, today)
            } else {
                emptyList()
            },
            listing = listingPage,
            flash = when {
                answer == "accepted" && state == AnswerState.ACCEPTED -> "C'est noté : ce logement vous intéresse."
                answer == "rejected" && state == AnswerState.REJECTED -> "C'est noté : vous avez écarté ce logement."
                else -> null
            },
        )
    }

    private fun state(match: Match, listing: Listing?): AnswerState = when {
        listing?.status != ListingStatus.PUBLISHED -> AnswerState.UNAVAILABLE
        match.status == MatchStatus.PROPOSED -> AnswerState.PENDING
        match.status == MatchStatus.ACCEPTED -> AnswerState.ACCEPTED
        match.status == MatchStatus.REJECTED -> AnswerState.REJECTED
        else -> AnswerState.UNAVAILABLE
    }

    /** "Pour votre recherche : appartement 3 chambres à Bonapriso, jusqu'à 250K FCFA / mois." */
    private fun searchSummary(mandate: Mandate, locations: List<Location>, tenant: Tenant, formatter: TenantFormatter): String {
        val type = ListingPages.PROPERTY_TYPE_LABELS[mandate.propertyType]?.lowercase() ?: "logement"
        val places = mandate.neighborhoodIds.mapNotNull { id -> locations.find { it.id == id }?.name }
            .ifEmpty { listOfNotNull(locations.find { it.id == mandate.cityId }?.name) }
        val what = listOfNotNull(type, mandate.rooms?.let { rooms(it) }).joinToString(" ") +
            (places.ifEmpty { null }?.let { " à " + join(it, " ou ") } ?: "")
        val budget = mandate.price.takeIf { it.amount > 0 }?.let { price ->
            val amount = formatter.compactMoney(price.amount, price.currency.ifBlank { tenant.currencyCode })
            "jusqu'à " + listOfNotNull(amount, ListingPages.PERIODS[price.periodicity]).joinToString(" ")
        }
        return "Pour votre recherche : " + listOfNotNull(what, budget).joinToString(", ") + "."
    }

    /** A mandate's rooms ("3", "5+") as "3 chambres", "5 chambres et +"; null when it isn't a count. */
    private fun rooms(value: String): String? {
        val count = value.trim().removeSuffix("+").trim().toIntOrNull() ?: return null
        return "$count ${if (count > 1) "chambres" else "chambre"}" + if (value.trim().endsWith("+")) " et +" else ""
    }

    private fun reasons(
        match: Match,
        mandate: Mandate,
        listing: Listing,
        price: String,
        locations: List<Location>,
        formatter: TenantFormatter,
        today: LocalDate,
    ): List<FitReason> {
        val neighborhood = locations.find { it.id == listing.neighborhoodId }?.name
        val movingIn = mandate.movingPeriod != MovingPeriod.UNKNOWN || mandate.availableFrom != null
        val available = listing.availableFrom?.takeIf { it.isAfter(today) }
        return listOfNotNull(
            neighborhood?.takeIf { match.distanceKm == 0.0 }?.let { "À $it, le quartier que vous cherchez" },
            price.takeIf { match.priceScore == 1.0 && mandate.price.amount > 0 }?.let { "$it, dans votre budget" },
            listing.rooms?.takeIf { match.roomsScore == 1.0 && !mandate.rooms.isNullOrBlank() }
                ?.let { "$it ${if (it > 1) "chambres" else "chambre"}, comme demandé" },
            ListingPages.FURNITURE_LABELS[listing.furnitureType]
                ?.takeIf { match.furnitureScore == 1.0 && mandate.furnitureType != null }
                ?.let { "$it, comme demandé" },
            if (match.availabilityScore == 1.0 && movingIn) {
                available?.let { "Disponible le ${formatter.date(it)}, avant votre emménagement" } ?: "Disponible immédiatement"
            } else {
                null
            },
        ).map { FitReason(it) } + listOfNotNull(amenities(match, mandate))
    }

    /** "Réservoir d'eau, clôturé et sécurisé, mais sans groupe électrogène"; null when none is missing. */
    private fun amenities(match: Match, mandate: Mandate): FitReason? {
        val searched = AMENITIES.filter { (field, _, wanted) -> wanted(mandate) || field in match.missingAmenities }
        val (missing, present) = searched.partition { (field) -> field in match.missingAmenities }
        if (missing.isEmpty()) {
            return null
        }
        val without = join(missing.map { it.second }, " ni ")
        val text = if (present.isEmpty()) {
            "Sans $without"
        } else {
            present.joinToString(", ") { it.second }.replaceFirstChar { it.uppercase() } + ", mais sans $without"
        }
        return FitReason(text, partial = true)
    }

    /** "a", "a ou b", "a, b ou c". */
    private fun join(items: List<String>, last: String): String =
        if (items.size <= 1) items.joinToString("") else items.dropLast(1).joinToString(", ") + last + items.last()
}
