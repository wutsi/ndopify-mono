package com.wutsi.ndopify.web.ui.visit

import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.ui.listing.ListingPage

/**
 * The page of a visit, as [VisitPages] builds it: every text already formatted, so `visit.html` only prints it. Texts
 * are null when the visit doesn't say them, so the template leaves the blocks out. [listing] and [agent] are null when
 * the server didn't return them.
 */
data class VisitPage(
    val id: Long,
    val status: VisitStatus,
    /** "Demandée", "Confirmée", "En cours", "Terminée", "Annulée", "Expirée", "Non effectuée" ("Visite" when unknown). */
    val statusLabel: String,
    /** "info", "success" or "neutral": the badge's tone, matching `visite.js`. */
    val statusTone: String,
    val type: VisitType,
    /** "Sur place" / "Visite vidéo" ("Visite" when unknown). */
    val typeLabel: String,
    /** "Sur place · samedi 7 novembre 2026 à 10:30". */
    val requestedSlot: String?,
    val scheduledSlot: String?,
    /** Only for a CONFIRMED visit whose [scheduledSlot] differs from [requestedSlot]. */
    val changedSlot: String?,
    val note: String?,
    /** The 4-digit code the customer gives the agent to start the visit: only ever to the customer, never invented. */
    val code: String?,
    val address: String?,
    val mapUrl: String?,
    /** The customer's WhatsApp number for a VIDEO visit. */
    val videoNumber: String?,
    val checkedInAt: String?,
    val cancelledAt: String?,
    val cancelledBy: VisitParty?,
    val cancellationReason: String?,
    val noShowParty: VisitParty?,
    val noShowReason: VisitNoShowReason?,
    val canCancel: Boolean,
    /** The outcome of the cancellation just posted, null otherwise. */
    val flash: String?,
    val listing: ListingPage?,
    val agent: VisitAgent?,
    /** "/match/{matchId}", back to the proposal. */
    val matchUrl: String,
)

/** The visit's agent, as [VisitPages] builds it: name and photo for the summary, WhatsApp for contact. */
data class VisitAgent(val name: String, val initials: String, val photoUrl: String?, val whatsappUrl: String?)
