package com.wutsi.ndopify.web.ui.match

import com.wutsi.ndopify.web.ui.listing.ListingPage

/** Which answer block `match.html` renders (see [MatchPages] for how a match gets one). */
enum class AnswerState { PENDING, ACCEPTED, REJECTED, UNAVAILABLE }

/**
 * The page of a match proposed to its customer, as [MatchPages] builds it: every text already formatted. [listing] is
 * null, and [reasons] empty, when the listing can't be shown anymore ([AnswerState.UNAVAILABLE]).
 */
data class MatchPage(
    val id: Long,
    val state: AnswerState,
    /** "Serge vous propose ce logement". */
    val title: String,
    /** "Pour votre recherche : appartement 3 chambres à Bonapriso, jusqu'à 250K FCFA / mois." */
    val searchSummary: String,
    /** "Serge", or "votre agent" when the agent has no name: for the sentences that name it. */
    val agentFirstName: String,
    val agentName: String,
    /** Shown when the agent has no [agentPhotoUrl]. */
    val agentInitials: String,
    val agentPhotoUrl: String?,
    /** `https://wa.me/<digits>`, null without a WhatsApp number. */
    val agentWhatsappUrl: String?,
    /** "Pourquoi ce logement ?". */
    val reasons: List<FitReason>,
    val listing: ListingPage?,
    /** The outcome of the answer just posted, null otherwise. */
    val flash: String?,
)

/** A line of "Pourquoi ce logement ?": [partial] for a criterion only partly met (some amenities missing). */
data class FitReason(val text: String, val partial: Boolean = false)
