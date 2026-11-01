package com.wutsi.ndopify.npc.ui.match

import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.time.LocalDate

/** Builds the mandate page's *Biens correspondants* from the matches and their listings. */
object MatchViews {
    /**
     * [matches] or [listings] null (their search failed) is [MatchSectionView.unavailable]. A match whose listing isn't
     * in [listings] (unpublished meanwhile) is left out; DISMISSED ones are only counted.
     */
    fun section(
        mandate: Mandate,
        matches: List<Match>?,
        listings: Map<Long, ListingSummary>?,
        locations: Map<Long, Location>,
        today: LocalDate,
        formatter: TenantFormatter,
    ): MatchSectionView {
        if (matches == null || listings == null) {
            return MatchSectionView(unavailable = true)
        }
        return MatchSectionView(
            matches = MatchRules.sort(matches).mapNotNull { match ->
                listings[match.listingId]?.let { view(match, mandate, it, locations, today, formatter) }
            },
            dismissed = matches.count { it.status == MatchStatus.DISMISSED },
        )
    }

    private fun view(
        match: Match,
        mandate: Mandate,
        listing: ListingSummary,
        locations: Map<Long, Location>,
        today: LocalDate,
        formatter: TenantFormatter,
    ): MatchView {
        val type = MandateLabels.shortPropertyType(listing.propertyType) ?: "Bien"
        val available = listing.availableFrom?.takeIf { it.isAfter(today) }
        return MatchView(
            id = match.id,
            listingId = listing.id,
            title = listOfNotNull(type, listing.rooms?.let { MatchRules.rooms(it) }).joinToString(" · "),
            tag = MatchRules.tag(match),
            score = MatchRules.score(match),
            listingUrl = "/listings/${listing.id}",
            facts = listOf(
                "Quartier" to (
                    listing.neighborhoodId?.let { locations[it]?.name }
                        ?: locations[listing.cityId]?.name
                        ?: "—"
                    ),
                "Loyer" to MandateLabels.price(
                    Money(listing.price, listing.currencyCode, listing.pricePeriodicityType),
                    formatter,
                ),
                "Disponible" to (available?.let { formatter.date(it) } ?: "Maintenant"),
            ),
            answer = MatchRules.answer(match, formatter),
            fit = MatchRules.fit(match, mandate, listing),
            actionable = match.status == MatchStatus.ACTIVE,
        )
    }
}
