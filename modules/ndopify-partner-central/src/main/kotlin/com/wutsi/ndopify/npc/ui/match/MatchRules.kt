package com.wutsi.ndopify.npc.ui.match

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.ui.mandate.Amenity
import com.wutsi.ndopify.npc.ui.mandate.MandateRules
import com.wutsi.ndopify.web.core.service.TenantFormatter
import kotlin.math.roundToInt

/**
 * The wording of the listings ndopify-server matched to a signed search (`Matcher`'s partial scores as "Pourquoi ce
 * bien ?", tags, the client's answer, the card summaries), after the prototype's `renderMatches`.
 *
 * Not rendered, though the prototype has them: *Retenir* (SHORTLISTED) and sending several at once, the client's
 * visit request and coded rejection reasons, the distance of a nearby neighbourhood and the names of the missing
 * amenities. A SHORTLISTED match isn't shown at all: partner-central never shortlists one.
 */
object MatchRules {
    /** The statuses shown, in the section's order (best score first within each). DISMISSED ones are only counted. */
    val ORDER: List<MatchStatus> = listOf(MatchStatus.ACCEPTED, MatchStatus.ACTIVE, MatchStatus.PROPOSED, MatchStatus.REJECTED)

    /** What the mandate page searches: [ORDER] and DISMISSED, to count them. */
    val SEARCHED: List<MatchStatus> = ORDER + MatchStatus.DISMISSED

    /** In a match's fit, unlike `MandateLabels.furniture` ("Oui" / "Non"). */
    val FURNITURE_LABELS: Map<FurnitureType, String> = mapOf(
        FurnitureType.UNFURNISHED to "Non meublé",
        FurnitureType.FULLY_FURNISHED to "Meublé",
    )

    private val TAGS = mapOf(
        MatchStatus.ACTIVE to MandateRules.Tag("Nouveau", "info"),
        MatchStatus.PROPOSED to MandateRules.Tag("Envoyé", "neutral"),
        MatchStatus.ACCEPTED to MandateRules.Tag("Intéressé", "success"),
        MatchStatus.REJECTED to MandateRules.Tag("Refusé", "neutral"),
    )

    private val SENT = setOf(MatchStatus.PROPOSED, MatchStatus.ACCEPTED, MatchStatus.REJECTED)

    // A partial score this close to 1 is full
    private const val FULL = 0.999

    /** Only a signed search has matches: the matcher runs once the customer signs. */
    fun matched(mandate: Mandate): Boolean =
        mandate.mandateType == MandateType.SEARCH && mandate.status == MandateStatus.APPROVED

    /** The matches in [ORDER], best score first within each status; the others left out. */
    fun sort(matches: List<Match>): List<Match> =
        matches.filter { it.status in ORDER }
            .sortedWith(compareBy<Match> { ORDER.indexOf(it.status) }.thenByDescending { it.score })

    fun tag(match: Match): MandateRules.Tag = TAGS[match.status] ?: TAGS.getValue(MatchStatus.REJECTED)

    /** Where a sent match stands: "Envoyé le 28 oct. 2026 · le client est intéressé."; null before it's sent. */
    fun answer(match: Match, formatter: TenantFormatter): String? {
        val sent = "Envoyé" + (match.proposedAt?.let { " le " + formatter.date(it) } ?: "")
        return when (match.status) {
            MatchStatus.PROPOSED -> "$sent · le client n'a pas encore répondu."
            MatchStatus.ACCEPTED -> "$sent · le client est intéressé."
            MatchStatus.REJECTED -> {
                val answered = match.answeredAt?.let { " le " + formatter.date(it) } ?: ""
                val reason = match.rejectionReason?.trim()?.ifEmpty { null }?.let { " : $it" } ?: ""
                "$sent · refusé$answered$reason."
            }
            else -> null
        }
    }

    /**
     * "Pourquoi ce bien ?": neighbourhood, price, rooms, amenities (only when some were asked), availability and
     * furniture (only when asked), from the match's partial scores, which ndopify-server's `Matcher` computes.
     */
    fun fit(match: Match, mandate: Mandate, listing: ListingSummary): List<FitLine> =
        listOfNotNull(
            neighborhood(match),
            price(match, mandate, listing),
            rooms(match, mandate, listing),
            amenities(match, mandate),
            availability(match),
            furniture(match, mandate, listing),
        )

    /** A card's "3 nouveaux biens correspondants" (ACTIVE ones); null without. */
    fun summary(matches: List<Match>): String? {
        val count = matches.count { it.status == MatchStatus.ACTIVE }
        return when (count) {
            0 -> null
            1 -> "1 nouveau bien correspondant"
            else -> "$count nouveaux biens correspondants"
        }
    }

    /** A card's "3 envoyés · 1 intéresse le client · 1 refusé · 1 sans réponse"; null when none was sent. */
    fun proposals(matches: List<Match>): String? {
        val sent = matches.count { it.status in SENT }
        if (sent == 0) {
            return null
        }
        val accepted = matches.count { it.status == MatchStatus.ACCEPTED }
        val rejected = matches.count { it.status == MatchStatus.REJECTED }
        val pending = matches.count { it.status == MatchStatus.PROPOSED }
        return listOfNotNull(
            "$sent " + plural(sent, "envoyé", "envoyés"),
            accepted.takeIf { it > 0 }?.let { "$it " + plural(it, "intéresse", "intéressent") + " le client" },
            rejected.takeIf { it > 0 }?.let { "$it " + plural(it, "refusé", "refusés") },
            pending.takeIf { it > 0 }?.let { "$it sans réponse" },
        ).joinToString(" · ")
    }

    /** A card's next step, before the mandate's: a visit for the best ACCEPTED one, else sending the ACTIVE ones. */
    fun nextStep(matches: List<Match>): String? {
        val sorted = sort(matches)
        sorted.firstOrNull { it.status == MatchStatus.ACCEPTED }?.let {
            return "L'annonce n° ${it.listingId} intéresse le client : proposez-lui une visite."
        }
        return if (sorted.any { it.status == MatchStatus.ACTIVE }) {
            "Envoyez au client les biens qui lui correspondent."
        } else {
            null
        }
    }

    /** "95 %", with a no-break space. */
    fun score(match: Match): String = percent((match.score * 100).roundToInt())

    /** "3 chambres", "1 chambre". */
    fun rooms(count: Int): String = "$count " + plural(count, "chambre", "chambres")

    private fun neighborhood(match: Match): FitLine =
        when {
            match.neighborhoodScore >= FULL -> FitLine(true, "Dans le secteur recherché")
            match.neighborhoodScore > 0 -> FitLine(false, "Près du secteur recherché")
            else -> FitLine(false, "Hors du secteur recherché")
        }

    private fun price(match: Match, mandate: Mandate, listing: ListingSummary): FitLine {
        val budget = mandate.price.amount
        if (match.priceScore >= FULL || budget <= 0) {
            return FitLine(true, "Dans le budget")
        }
        // At least 1 %: a partial score under 1 is over the budget
        val over = ((listing.price - budget) * 100.0 / budget).roundToInt().coerceAtLeast(1)
        return FitLine(false, percent(over) + " au-dessus du budget")
    }

    private fun rooms(match: Match, mandate: Mandate, listing: ListingSummary): FitLine? {
        val searched = mandate.rooms?.trim()?.ifEmpty { null }
        val count = listing.rooms
            ?: return searched?.let { FitLine(match.roomsScore >= FULL, "Nombre de chambres non précisé") }
        val text = rooms(count)
        return when {
            match.roomsScore >= FULL -> FitLine(true, if (searched == null) text else "$text, comme demandé")
            match.roomsScore >= 0.5 -> FitLine(false, "$text, une de plus que demandé")
            match.roomsScore > 0 -> FitLine(false, "$text, une de moins que demandé")
            else -> FitLine(false, "$text au lieu de $searched")
        }
    }

    private fun amenities(match: Match, mandate: Mandate): FitLine? {
        // As ndopify-server's Matcher counts them: a track only reachable on foot isn't an amenity
        val requested = (Amenity.of(mandate) - Amenity.TRACK_INACCESSIBLE_BY_CAR).size
        if (requested == 0) {
            return null
        }
        val have = (match.amenitiesScore * requested).roundToInt()
        return when {
            have < requested -> FitLine(false, "$have " + plural(have, "équipement", "équipements") + " sur $requested")
            requested == 1 -> FitLine(true, "L'équipement demandé")
            else -> FitLine(true, "Les $requested équipements demandés")
        }
    }

    private fun availability(match: Match): FitLine =
        when {
            match.availabilityScore >= FULL -> FitLine(true, "Disponible avant la date d'emménagement")
            match.availabilityScore > 0 -> FitLine(false, "Disponible moins d'un mois après la date d'emménagement")
            else -> FitLine(false, "Disponible plus d'un mois après la date d'emménagement")
        }

    private fun furniture(match: Match, mandate: Mandate, listing: ListingSummary): FitLine? {
        val wanted = FURNITURE_LABELS[mandate.furnitureType] ?: return null
        if (match.furnitureScore >= FULL) {
            return FitLine(true, "$wanted, comme demandé")
        }
        val listed = FURNITURE_LABELS[listing.furnitureType] ?: "Ameublement non précisé"
        return FitLine(false, "$listed : le client le veut ${wanted.lowercase()}")
    }

    private fun percent(value: Int): String = "$value %"

    private fun plural(count: Int, one: String, many: String): String = if (count > 1) many else one
}
