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
)

/**
 * The *Visites* section of the Espace agent, a signed search's mandate page and a landlord's listing mandate page: the
 * requests to confirm, then the visits to come.
 */
data class VisitCardsView(
    val requested: List<VisitCardView>,
    val upcoming: List<VisitCardView>,
    val unavailable: Boolean = false,
)
