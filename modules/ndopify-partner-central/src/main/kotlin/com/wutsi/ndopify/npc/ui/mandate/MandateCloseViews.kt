package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateSummary
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.web.core.service.TenantFormatter

/** A counterpart choice of the closing wizard: the listing a search ended on, or the client a listing was rented to. */
data class CloseCandidateView(
    val matchId: Long,
    /** "Appartement · 3 chambres" (a listing), or the client's name (a listing's candidate). */
    val title: String,
    /** "annonce n° 3012" for a listing; null for a client, whose mandate's number is in [title]. */
    val reference: String?,
    /** The listing's neighbourhood; null for a client. */
    val neighbourhood: String?,
    /** "240K FCFA / mois" for a listing; null for a client. */
    val rent: String?,
    /** The raw listing price amount, for the price step's pre-fill; null when the listing's price is unknown. */
    val priceAmount: Long?,
    /** "Intéressé le 29 oct. 2026", null before the client answered (never shown). */
    val interestLabel: String?,
)

/** One step of the wizard's progress, as the template renders it. */
data class CloseStepView(
    val name: String,
    val label: String,
)

/**
 * What the confirm step recaps: the deal's two sides, the agreed rent (already formatted), and what closing does.
 * Each label is a finished string, so the template never formats an amount itself.
 */
data class CloseRecapView(
    val property: String,
    val client: String,
    val rent: String,
    val note: String,
)

/**
 * The closing wizard's page view: the mandate, its counterpart candidates, and where the wizard stands. Built from the
 * DTOs by [MandateCloseViews], which holds no HTTP concerns.
 */
data class MandateCloseView(
    val mandateId: Long,
    val title: String,
    /** A landlord's listing, not a search. */
    val owner: Boolean,
    val candidates: List<CloseCandidateView>,
    /** The candidates could not be read: the step shows the "unavailable" note and the outside option only. */
    val candidatesUnavailable: Boolean,
    val step: String,
    val stepNumber: Int,
    val stepCount: Int,
    val steps: List<CloseStepView>,
    /** The tenant's currency symbol ("FCFA"), for the price suffix. */
    val currency: String,
    /** The confirm step's recap, null on the other steps. */
    val recap: CloseRecapView? = null,
)

/**
 * The labels of the closing wizard's candidates, built from `Match` + `ListingSummary` (a search's listing choices:
 * title, reference, neighbourhood, rent) or from `Match` + the client's mandate and customer (a listing's client
 * choices). The interest label comes from [Match.answeredAt].
 */
object MandateCloseViews {
    /** "Intéressé le 29 oct. 2026"; null when the client never answered (an ACCEPTED match is always answered). */
    fun interestLabel(match: Match, formatter: TenantFormatter): String? =
        match.answeredAt?.let { "Intéressé le " + formatter.date(it) }

    /** The counterpart step's label: a landlord closes with a client, a search with a listing. */
    private fun counterpartLabel(owner: Boolean): String = if (owner) "Client" else "Bien"

    /** The wizard's steps in order, without the counterpart one when it doesn't apply. */
    fun steps(counterpartApplicable: Boolean, owner: Boolean): List<CloseStepView> =
        listOfNotNull(
            CloseStepView(PendingMandateClose.STEP_COUNTERPART, counterpartLabel(owner)).takeIf { counterpartApplicable },
            CloseStepView(PendingMandateClose.STEP_PRICE, "Loyer"),
            CloseStepView(PendingMandateClose.STEP_CONFIRM, "Confirmation"),
        )

    fun of(
        mandate: Mandate,
        candidates: List<CloseCandidateView>,
        candidatesUnavailable: Boolean,
        step: String,
        steps: List<CloseStepView>,
        currency: String,
        recap: CloseRecapView? = null,
    ): MandateCloseView = MandateCloseView(
        mandateId = mandate.id,
        title = MandateLabels.title(mandate),
        owner = mandate.mandateType == MandateType.LISTING,
        candidates = candidates,
        candidatesUnavailable = candidatesUnavailable,
        step = step,
        stepNumber = steps.indexOfFirst { it.name == step } + 1,
        stepCount = steps.size,
        steps = steps,
        currency = currency,
        recap = recap,
    )

    /** A search's candidate: the listing it ended on, as a compact card ("Appartement · 3 chambres, Bonapriso"). */
    fun candidate(
        match: Match,
        listing: ListingSummary,
        locations: Map<Long, Location>,
        formatter: TenantFormatter,
    ): CloseCandidateView {
        val type = MandateLabels.shortPropertyType(listing.propertyType) ?: "Bien"
        return CloseCandidateView(
            matchId = match.id,
            title = listOfNotNull(type, listing.rooms?.let { MatchRules.rooms(it) }).joinToString(" · "),
            reference = "annonce n° ${listing.id}",
            neighbourhood = listing.neighborhoodId?.let { locations[it]?.name }
                ?: locations[listing.cityId]?.name
                ?: "—",
            rent = MandateLabels.price(
                Money(listing.price, listing.currencyCode, listing.pricePeriodicityType),
                formatter,
                compact = true,
            ),
            priceAmount = listing.price.takeIf { it > 0 },
            interestLabel = interestLabel(match, formatter),
        )
    }

    /** A listing's candidate: the client it was rented to, named by its search's customer (its number without one). */
    fun candidate(
        match: Match,
        mandate: MandateSummary,
        customer: CustomerSummary?,
        listingPrice: Long?,
        formatter: TenantFormatter,
    ): CloseCandidateView = CloseCandidateView(
        matchId = match.id,
        title = customer?.party
            ?.let { listOf(it.firstName, it.lastName).filter { name -> name.isNotBlank() }.joinToString(" ") }
            ?.ifBlank { null }
            ?: "Mandat n° ${mandate.id}",
        reference = null,
        neighbourhood = null,
        rent = null,
        priceAmount = listingPrice,
        interestLabel = interestLabel(match, formatter),
    )

    /**
     * The confirm step's recap: the deal's two sides, the agreed rent and what closing does. [matchId] is the stored
     * counterpart match (null for an outside deal, or when the counterpart step was skipped): it drives the note,
     * which states exactly what will be closed. [selected] is that match resolved for the labels (null when the
     * candidates can't be read, even though [matchId] stays set); [listing] the landlord's own listing ([owner]);
     * [customer] the search's customer. Both are secondary: a missing one leaves its line on a fallback, never a
     * failure.
     */
    fun recap(
        mandate: Mandate,
        matchId: Long?,
        selected: CloseCandidateView?,
        outside: Boolean,
        listing: Listing?,
        customer: Party?,
        locations: Map<Long, Location>,
        amount: Long,
        formatter: TenantFormatter,
    ): CloseRecapView {
        val owner = mandate.mandateType == MandateType.LISTING
        return CloseRecapView(
            property = propertyLabel(owner, selected, outside, listing, locations),
            client = clientLabel(owner, selected, outside, customer),
            rent = formatter.money(amount) + " / mois",
            note = note(owner, matchId != null, mandate.id),
        )
    }

    /** The deal's property: a search's chosen listing, or a landlord's own listing (its "Bien" is always the listing). */
    private fun propertyLabel(
        owner: Boolean,
        selected: CloseCandidateView?,
        outside: Boolean,
        listing: Listing?,
        locations: Map<Long, Location>,
    ): String = when {
        owner -> listing?.let { listingLabel(it, locations) } ?: "Un bien hors de vos annonces"
        outside -> "Un bien hors de vos annonces"
        selected != null -> "${selected.title}, ${selected.neighbourhood} — ${selected.reference}"
        else -> "Un bien"
    }

    /** The deal's client: a listing's chosen client, or a search's customer (the person who signs). */
    private fun clientLabel(owner: Boolean, selected: CloseCandidateView?, outside: Boolean, customer: Party?): String =
        when {
            owner -> when {
                outside -> "Un client hors de vos recherches"
                selected != null -> selected.title
                else -> "Un client"
            }
            else -> partyName(customer) ?: "Le client"
        }

    /** "Appartement · 2 chambres, Bonapriso — annonce n° 501", the landlord's own listing. */
    private fun listingLabel(listing: Listing, locations: Map<Long, Location>): String {
        val type = MandateLabels.shortPropertyType(listing.propertyType) ?: "Bien"
        val title = listOfNotNull(type, listing.rooms?.let { MatchRules.rooms(it) }).joinToString(" · ")
        val neighbourhood = listing.neighborhoodId?.let { locations[it]?.name }
            ?: locations[listing.cityId]?.name
            ?: "—"
        return "$title, $neighbourhood — annonce n° ${listing.id}"
    }

    /** "Sandrine Eboa", or null without a name. */
    private fun partyName(party: Party?): String? =
        party?.let { listOf(it.firstName, it.lastName).filter { name -> name.isNotBlank() }.joinToString(" ") }
            ?.ifBlank { null }

    /** What closing does, keyed on whether a counterpart match is stored: both sides of the deal, or this mandate alone. */
    private fun note(owner: Boolean, hasMatch: Boolean, id: Long): String = when {
        owner && hasMatch ->
            "À la confirmation : le mandat n° $id est fermé, l'annonce est retirée du marché et la recherche du client est fermée. Cette action est définitive."
        owner ->
            "À la confirmation : le mandat n° $id est fermé et l'annonce est retirée du marché. Cette action est définitive."
        hasMatch ->
            "À la confirmation : le mandat n° $id est fermé, l'annonce est retirée du marché et le mandat du bailleur est fermé. Cette action est définitive."
        else ->
            "À la confirmation : le mandat n° $id est fermé. Aucune annonce n'est retirée du marché. Cette action est définitive."
    }
}
