package com.wutsi.ndopify.web.ui.match

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.core.service.TenantFormatter
import com.wutsi.ndopify.web.ui.listing.ListingPages
import com.wutsi.ndopify.web.ui.visit.VisitSlots
import java.time.LocalDate

/**
 * Builds the [MatchPage] of a proposed match (the prototype `proposition.html`) from ndopify-server's DTOs; the
 * listing's part is [ListingPages]'s, so never its `agentRemarks`.
 *
 * The state: [AnswerState.UNAVAILABLE] when the listing couldn't be read (null: a 403/404 to the customer) or isn't
 * PUBLISHED anymore, whatever the answer, since nothing can be done with it; otherwise PROPOSED → PENDING, ACCEPTED,
 * REJECTED, EXPIRED (not answered in time: no answer anymore, the agent may propose it again; any other status, which
 * ndopify-server doesn't show the customer, → UNAVAILABLE).
 *
 * "Pourquoi ce logement ?" only says what is true and was searched: one line per criterion fully met (score 1.0) that
 * the mandate sets (the neighbourhood when the listing is inside a searched one, `distanceKm == 0`), and the searched
 * amenities, matched or missing. The flash (`answer`: `accepted` / `rejected`, set by the answer's
 * redirect) is only shown when the match is in that state, so a hand-typed `?answer=` says nothing false. The page says
 * the agent is told of the answer: ndopify-server notifies it when the match is answered (Telegram, else e-mail).
 *
 * The visit block appears once the match is ACCEPTED: the open visit when one exists, otherwise the request form when
 * the mandate is approved. An UNAVAILABLE match still shows its open visit when the listing went away after the
 * customer accepted it, since the visit is still alive; it never offers the form then. The `visit` flash (`requested`,
 * `invalid-slot`, `conflict`, set by the request's redirect) follows the same rule as the answer's.
 *
 * The mandate's `listingType` picks the wording: a SALE search speaks of a *bien* and of the purchase (*avant votre
 * achat*), a RENTAL search keeps the *logement* and the moving-in (*avant votre emménagement*).
 *
 * A building sold as a whole reads its unités and its étages instead of its rooms, its furniture and its amenities:
 * the fit lines give what it states, its search summary is *immeuble de 12 unités sur 3 étages*, and it lists no
 * searched amenity.
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
        customer: Customer? = null,
        openVisit: VisitSummary? = null,
        visit: String? = null,
    ): MatchPage {
        val state = state(match, listing)
        val sale = mandate.listingType == ListingType.SALE
        val visitFee = mandate.visitFee.takeIf { it.amount > 0 }
            ?.let { formatter.compactMoney(it.amount, it.currency) }
        val termsNote = "Les visites sont organisées par votre agent. " +
            (
                visitFee?.let { "Frais de visite : $it par visite, réglés directement à lui." }
                    ?: "Aucun frais de visite."
                )
        val listingPage = listing?.takeIf { state != AnswerState.UNAVAILABLE }
            ?.let { ListingPages.of(it, images, locations, tenant, formatter, today).copy(termsNote = termsNote) }
        val firstName = agent.party.firstName.trim().ifEmpty { NO_AGENT_NAME }
        val fullName = "${agent.party.firstName} ${agent.party.lastName}".trim().ifEmpty { NO_AGENT_NAME }
        val open = openVisit?.let { OpenVisit(it.id, typeLabel(it.type), slot(it, tenant, formatter), it.status) }
        val panel = when {
            state == AnswerState.ACCEPTED -> VisitPanel(
                open,
                if (open == null && mandate.status == MandateStatus.APPROVED) {
                    VisitForm(
                        days = VisitSlots.days(today, formatter),
                        times = VisitSlots.times(),
                        contact = contact(customer),
                        video = customer?.whatsappNumber?.isNotBlank() == true,
                    )
                } else {
                    null
                },
            )
            state == AnswerState.UNAVAILABLE && match.status == MatchStatus.ACCEPTED && open != null ->
                VisitPanel(open, null)
            else -> null
        }

        return MatchPage(
            id = match.id,
            state = state,
            sale = sale,
            title = ("$firstName vous propose " + (if (sale) "ce bien" else "ce logement"))
                .replaceFirstChar { it.uppercase() },
            searchSummary = searchSummary(mandate, locations, tenant, formatter, sale),
            agentFirstName = firstName,
            agentName = fullName.replaceFirstChar { it.uppercase() },
            agentInitials = listOf(agent.party.firstName, agent.party.lastName)
                .mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }
                .joinToString(""),
            agentPhotoUrl = agent.party.photoUrl?.ifBlank { null },
            agentWhatsappUrl = agent.whatsappNumber?.filter { it.isDigit() }?.ifEmpty { null }?.let { "https://wa.me/$it" },
            reasons = if (listing != null && listingPage != null) {
                reasons(match, mandate, listing, listingPage.price, locations, formatter, today, sale)
            } else {
                emptyList()
            },
            criteria = if (listingPage != null) criteria(mandate, match) else emptyList(),
            listing = listingPage,
            visitFee = visitFee,
            flash = when {
                answer == "accepted" && state == AnswerState.ACCEPTED ->
                    "C'est noté : " + (if (sale) "ce bien" else "ce logement") + " vous intéresse, votre agent est prévenu."
                answer == "rejected" && state == AnswerState.REJECTED ->
                    "C'est noté : vous avez écarté " + (if (sale) "ce bien" else "ce logement") + ", votre agent est prévenu."
                else -> null
            },
            visit = panel,
            visitFlash = visitFlash(visit, panel),
        )
    }

    /** "Sur place" / "Visite vidéo"; "Visite" for a type the page doesn't know. */
    private fun typeLabel(type: VisitType): String = when (type) {
        VisitType.ON_SITE -> "Sur place"
        VisitType.VIDEO -> "Visite vidéo"
        else -> "Visite"
    }

    /** The visit's booked time, or the requested one until the agent confirms a date. */
    private fun slot(visit: VisitSummary, tenant: Tenant, formatter: TenantFormatter): String =
        VisitSlots.label(typeLabel(visit.type), visit.scheduledAt ?: visit.requestedAt, tenant, formatter)

    /** "Sandrine Eboa · WhatsApp +237699887766"; null when the customer is unknown or says neither. */
    private fun contact(customer: Customer?): String? {
        val name = listOfNotNull(
            customer?.party?.firstName?.trim()?.ifEmpty { null },
            customer?.party?.lastName?.trim()?.ifEmpty { null },
        ).joinToString(" ")
        return listOfNotNull(
            name.ifEmpty { null },
            customer?.whatsappNumber?.takeIf { it.isNotBlank() }?.let { "WhatsApp $it" },
        ).joinToString(" · ").ifEmpty { null }
    }

    /** The outcome of a visit request, from its redirect's `visit` parameter; "requested" only with an open visit. */
    private fun visitFlash(visit: String?, panel: VisitPanel?): String? = when (visit) {
        "requested" -> if (panel?.open != null) {
            "Votre demande de visite est envoyée. L'agent vous confirme la date par e-mail."
        } else {
            null
        }
        "invalid-slot" -> "Ce créneau n'est plus disponible. Choisissez-en un autre."
        "conflict" -> "La visite n'a pas pu être demandée. Rechargez la page ou contactez votre agent."
        else -> null
    }

    private fun state(match: Match, listing: Listing?): AnswerState = when {
        listing?.status != ListingStatus.PUBLISHED -> AnswerState.UNAVAILABLE
        match.status == MatchStatus.PROPOSED -> AnswerState.PENDING
        match.status == MatchStatus.ACCEPTED -> AnswerState.ACCEPTED
        match.status == MatchStatus.REJECTED -> AnswerState.REJECTED
        match.status == MatchStatus.EXPIRED -> AnswerState.EXPIRED
        else -> AnswerState.UNAVAILABLE
    }

    /** "Pour votre recherche : appartement 3 chambres à Bonapriso, jusqu'à 250K FCFA / mois." */
    private fun searchSummary(
        mandate: Mandate,
        locations: List<Location>,
        tenant: Tenant,
        formatter: TenantFormatter,
        sale: Boolean,
    ): String {
        val type = ListingPages.PROPERTY_TYPE_LABELS[mandate.propertyType]?.lowercase() ?: if (sale) "bien" else "logement"
        val places = mandate.neighborhoodIds.mapNotNull { id -> locations.find { it.id == id }?.name }
            .ifEmpty { listOfNotNull(locations.find { it.id == mandate.cityId }?.name) }
        val description = if (buildingSale(mandate)) {
            building(type, mandate)
        } else {
            listOfNotNull(type, mandate.rooms?.let { rooms(it) }).joinToString(" ")
        }
        val what = description + (places.ifEmpty { null }?.let { " à " + join(it, " ou ") } ?: "")
        val budget = mandate.price.takeIf { it.amount > 0 }?.let { price ->
            val amount = formatter.compactMoney(price.amount, price.currency.ifBlank { tenant.currencyCode })
            "jusqu'à " + listOfNotNull(amount, ListingPages.PERIODS[price.periodicity]).joinToString(" ")
        }
        return "Pour votre recherche : " + listOfNotNull(what, budget).joinToString(", ") + "."
    }

    /** "immeuble de 12 unités sur 3 étages": either count left out when the search didn't answer it. */
    private fun building(type: String, mandate: Mandate): String {
        val counts = listOfNotNull(
            mandate.units?.let { "$it " + plural(it, "unité", "unités") },
            mandate.levels?.let { "$it " + plural(it, "étage", "étages") },
        )
        return listOfNotNull(type, counts.joinToString(" sur ").ifEmpty { null }?.let { "de $it" })
            .joinToString(" ")
    }

    /** A building sold as a whole, as opposed to a rented building or any other sale. */
    private fun buildingSale(mandate: Mandate): Boolean =
        mandate.listingType == ListingType.SALE && mandate.propertyType == PropertyType.BUILDING

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
        sale: Boolean,
    ): List<FitReason> {
        val neighborhood = locations.find { it.id == listing.neighborhoodId }?.name
        val movingIn = mandate.movingPeriod != MovingPeriod.UNKNOWN || mandate.availableFrom != null
        val available = listing.availableFrom?.takeIf { it.isAfter(today) }
        val place = neighborhood?.takeIf { match.distanceKm == 0.0 }?.let { "À $it, le quartier que vous cherchez" }
        val budget = price.takeIf { match.priceScore == 1.0 && mandate.price.amount > 0 }?.let { "$it, dans votre budget" }
        val availability = if (match.availabilityScore == 1.0 && movingIn) {
            available
                ?.let { "Disponible le ${formatter.date(it)}, avant votre " + (if (sale) "achat" else "emménagement") }
                ?: if (sale) "Disponible avant votre achat" else "Disponible immédiatement"
        } else {
            null
        }
        // A building sold as a whole reads its unités and its étages instead of its rooms, furniture and amenities
        return if (buildingSale(mandate)) {
            listOfNotNull(place, budget, units(match, mandate, listing), levels(match, mandate, listing), availability)
        } else {
            listOfNotNull(
                place,
                budget,
                listing.rooms?.takeIf { match.roomsScore == 1.0 && !mandate.rooms.isNullOrBlank() }
                    ?.let { "$it ${if (it > 1) "chambres" else "chambre"}, comme demandé" },
                ListingPages.FURNITURE_LABELS[listing.furnitureType]
                    ?.takeIf { match.furnitureScore == 1.0 && mandate.furnitureType != null }
                    ?.let { "$it, comme demandé" },
                availability,
            )
        }.map { FitReason(it) }
    }

    /** A building sale's unités: no line when the search states none; a listing that states none says so. */
    private fun units(match: Match, mandate: Mandate, listing: Listing): String? {
        val searched = mandate.units ?: return null
        val count = listing.units ?: return "Nombre d'unités non précisé"
        val text = "$count " + plural(count, "unité", "unités")
        return if (match.unitsScore == 1.0) "$text, comme demandé" else "$text au lieu de $searched"
    }

    /** A building sale's étages, worded like its unités. */
    private fun levels(match: Match, mandate: Mandate, listing: Listing): String? {
        val searched = mandate.levels ?: return null
        val count = listing.levels ?: return "Nombre d'étages non précisé"
        val text = "$count " + plural(count, "étage", "étages")
        return if (match.levelsScore == 1.0) "$text, comme demandé" else "$text au lieu de $searched"
    }

    /**
     * The amenities the customer searched for, each matched or missing, worded as on the listing page; none for a
     * building sale, which asks no amenity.
     */
    private fun criteria(mandate: Mandate, match: Match): List<AmenityMatch> =
        if (buildingSale(mandate)) {
            emptyList()
        } else {
            AMENITIES
                .filter { (_, _, wanted) -> wanted(mandate) }
                .map { (field, label, _) ->
                    AmenityMatch(label.replaceFirstChar { it.uppercase() }, field !in match.missingAmenities)
                }
        }

    private fun plural(count: Int, one: String, many: String): String = if (count > 1) many else one

    /** "a", "a ou b", "a, b ou c". */
    private fun join(items: List<String>, last: String): String =
        if (items.size <= 1) items.joinToString("") else items.dropLast(1).joinToString(", ") + last + items.last()
}
