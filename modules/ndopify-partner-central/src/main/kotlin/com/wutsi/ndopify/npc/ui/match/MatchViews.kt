package com.wutsi.ndopify.npc.ui.match

import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import com.wutsi.ndopify.npc.ui.visit.VisitCardView
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.time.LocalDate

/** The generic label of a co-brokerage card whose listing agent can't be read. */
private const val OTHER_AGENT = "Bien d'un autre agent"

/**
 * Builds the mandate page's *Biens correspondants* from the matches, their listings and the open visits. Each open
 * visit sits in the card of the match that led to it, and the cards are grouped by what the agent does next.
 */
object MatchViews {
    /**
     * [matches] or [listings] null (their search failed) is [MatchSectionView.unavailable]. A match whose listing isn't
     * in [listings] (unpublished meanwhile) is left out; REJECTED and DISMISSED ones are only counted. [visits] maps a
     * match id to its open visit: a requested one leads *À confirmer*, a confirmed or started one *Visites à venir*.
     */
    fun section(
        mandate: Mandate,
        matches: List<Match>?,
        listings: Map<Long, ListingSummary>?,
        visits: Map<Long, VisitCardView>,
        locations: Map<Long, Location>,
        today: LocalDate,
        formatter: TenantFormatter,
        agentNames: Map<Long, String> = emptyMap(),
    ): MatchSectionView {
        if (matches == null || listings == null) {
            return MatchSectionView(unavailable = true)
        }
        val ordered = matches
            .filter { it.status in MatchRules.ORDER }
            .sortedWith(
                compareBy<Match> { group(it, visits[it.id]).ordinal }
                    .thenComparator { a, b -> compare(a, b, visits) },
            )
        return MatchSectionView(
            matches = ordered.mapNotNull { match ->
                listings[match.listingId]?.let { view(match, mandate, it, visits[match.id], locations, today, formatter, agentNames) }
            },
            dismissed = matches.count { it.status == MatchStatus.DISMISSED },
            outcomes = MatchRules.outcomes(matches),
        )
    }

    /** Requested visits soonest expiry first, upcoming visits soonest first, the other groups by score. */
    private fun compare(a: Match, b: Match, visits: Map<Long, VisitCardView>): Int =
        when (group(a, visits[a.id])) {
            MatchGroup.REQUESTED -> compareNullable(visits[a.id]?.expiresAt, visits[b.id]?.expiresAt)
            MatchGroup.UPCOMING -> compareNullable(visits[a.id]?.at, visits[b.id]?.at)
            else -> b.score.compareTo(a.score)
        }

    /** Nulls last. */
    private fun compareNullable(a: Long?, b: Long?): Int =
        when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            else -> a.compareTo(b)
        }

    private fun group(match: Match, visit: VisitCardView?): MatchGroup =
        when {
            visit?.status == VisitStatus.REQUESTED -> MatchGroup.REQUESTED
            visit != null && visit.status in setOf(VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN) -> MatchGroup.UPCOMING
            match.status == MatchStatus.ACCEPTED -> MatchGroup.INTERESTED
            match.status == MatchStatus.ACTIVE -> MatchGroup.NEW
            match.status == MatchStatus.PROPOSED -> MatchGroup.WAITING
            else -> MatchGroup.EXPIRED
        }

    private fun view(
        match: Match,
        mandate: Mandate,
        listing: ListingSummary,
        visit: VisitCardView?,
        locations: Map<Long, Location>,
        today: LocalDate,
        formatter: TenantFormatter,
        agentNames: Map<Long, String>,
    ): MatchView {
        val type = MandateLabels.shortPropertyType(listing.propertyType) ?: "Bien"
        val available = listing.availableFrom?.takeIf { it.isAfter(today) }
        return MatchView(
            id = match.id,
            listingId = listing.id,
            coBrokerage = match.coBrokerage,
            origin = if (match.coBrokerage) agentNames[listing.agentId] ?: OTHER_AGENT else null,
            title = listOfNotNull(type, listing.rooms?.let { MatchRules.rooms(it) }).joinToString(" · "),
            tag = MatchRules.tag(match),
            listingUrl = if (match.coBrokerage) "/listings/${listing.id}?match=${match.id}" else "/listings/${listing.id}",
            facts = listOf(
                "Correspondance" to MatchRules.score(match),
                "Quartier" to (
                    listing.neighborhoodId?.let { locations[it]?.name }
                        ?: locations[listing.cityId]?.name
                        ?: "—"
                    ),
                "Loyer" to MandateLabels.price(
                    Money(listing.price, listing.currencyCode, listing.pricePeriodicityType),
                    formatter,
                    compact = true,
                ),
                "Disponibilité" to (available?.let { "Dès le " + formatter.date(it) } ?: "Libre maintenant"),
            ),
            answer = MatchRules.answer(match, formatter),
            fit = MatchRules.fit(match, mandate, listing),
            actionable = match.status in MatchRules.ACTIONABLE,
            visit = visit,
            sent = match.proposedAt?.let { "Envoyé le ${formatter.date(it)}" },
            group = group(match, visit),
        )
    }
}
