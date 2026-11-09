package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateSummary
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.npc.ui.match.MatchRules
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
}
