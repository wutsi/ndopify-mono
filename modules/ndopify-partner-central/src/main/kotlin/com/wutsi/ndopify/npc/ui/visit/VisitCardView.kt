package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.visit.dto.VisitStatus

/**
 * A visit as a card: the Espace agent's, a signed search's mandate page, and a landlord's listing mandate page.
 * Anti-bypass: no code, no note, no address — the listing's line only, until the visit's page.
 *
 * On the mandate page the same card is a block inside the match or proposal it belongs to: [status] and [kindLabel]
 * carry its state, [whenText] its wording, and [matchId] finds that match/proposal. The Espace agent keeps the older
 * tag-and-when wording.
 */
data class VisitCardView(
    val id: Long,
    /** The match the visit belongs to; null when the visit came without one. */
    val matchId: Long?,
    val customerName: String,
    /** "Sur place" or "Visite vidéo". */
    val type: String,
    val video: Boolean,
    val status: VisitStatus,
    /** "Visite demandée", "Visite vidéo confirmée", "Visite en cours", "Visité". */
    val kindLabel: String,
    /** "pour le …", "le …", "depuis 10:05". */
    val whenText: String,
    /** Null for a requested visit (the card shows the timer instead). */
    val tag: VisitRules.Tag?,
    val whenLabel: String,
    /** Epoch ms of the visit itself, for ordering; null without a requested or scheduled date. */
    val at: Long?,
    val listing: String,
    /** Epoch ms, for home.js' countdown; null once confirmed (no deadline left). */
    val expiresAt: Long?,
    val expiresIn: String?,
    val urgent: Boolean,
    /** The slot lists of a requested visit, for the home page's inline confirmation. */
    val days: List<VisitDay> = emptyList(),
    val times: List<Pair<String, String>> = emptyList(),
    val selectedDay: String? = null,
    val selectedTime: String? = null,
    /** True when the customer's requested date and time are both in the allowed slot lists. */
    val requestedSlotValid: Boolean = false,
)

/** A day of the upcoming visits ("Aujourd'hui", "Demain", "sam. 8 novembre"). */
data class VisitGroupView(val label: String, val visits: List<VisitCardView>)

/**
 * The *Visites* section of the Espace agent: the requests to confirm, then the visits to come. The mandate pages use
 * the same cards attached to their match or proposal card instead.
 */
data class VisitCardsView(
    val requested: List<VisitCardView>,
    val upcoming: List<VisitCardView>,
    val unavailable: Boolean = false,
    /** The checked-in visits, shown before the upcoming ones. */
    val ongoing: List<VisitCardView> = emptyList(),
    /** Every upcoming visit, grouped by day. */
    val upcomingGroups: List<VisitGroupView> = emptyList(),
    /** The first four upcoming visits, to keep the home page short. */
    val upcomingPreviewGroups: List<VisitGroupView> = emptyList(),
    /** The rest, behind "Voir les N autres visites". */
    val upcomingMoreGroups: List<VisitGroupView> = emptyList(),
    val upcomingMoreCount: Int = 0,
)
