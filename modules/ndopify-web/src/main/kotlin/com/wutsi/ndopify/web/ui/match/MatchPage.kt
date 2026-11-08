package com.wutsi.ndopify.web.ui.match

import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.web.ui.listing.ListingPage
import com.wutsi.ndopify.web.ui.listing.VisitDay

/** Which answer block `match.html` renders (see [MatchPages] for how a match gets one). */
enum class AnswerState { PENDING, ACCEPTED, REJECTED, EXPIRED, UNAVAILABLE }

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
    /** "Vos critères": the amenities the customer searched for, each matched or missing. */
    val criteria: List<AmenityMatch>,
    val listing: ListingPage?,
    /** The outcome of the answer just posted, null otherwise. */
    val flash: String?,
    /** The visit of an accepted match: the open visit, or the form to request one; null otherwise. */
    val visit: VisitPanel?,
    /** The outcome of the visit request just posted, null otherwise. */
    val visitFlash: String?,
)

/** A line of "Pourquoi ce logement ?": [partial] for a criterion only partly met (some amenities missing). */
data class FitReason(val text: String, val partial: Boolean = false)

/** A searched amenity and whether the listing has it: "Vos critères". */
data class AmenityMatch(val label: String, val matched: Boolean)

/**
 * The visit block of a match page: [open] once a visit exists (no [form] then), or [form] to request one. When the
 * listing went away after the customer accepted it, only [open] is set, since the visit is still alive.
 */
data class VisitPanel(val open: OpenVisit?, val form: VisitForm?)

/** An open visit as the match page shows it: [type] "Sur place" / "Visite vidéo", [slot] already formatted. */
data class OpenVisit(val id: Long, val type: String, val slot: String, val status: VisitStatus)

/** The form to request a visit: the offered slots and the customer's contact details, prefilled. */
data class VisitForm(
    val days: List<VisitDay>,
    val times: List<Pair<String, String>>,
    val contact: String?,
    val video: Boolean,
)
