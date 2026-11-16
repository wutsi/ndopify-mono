package com.wutsi.ndopify.match.server.mail

import com.wutsi.ndopify.match.dto.MatchRejectionReason

/**
 * How the match e-mails and messages word a rejected proposal, shared by `MatchAnsweredMailet` and
 * `MatchAnsweredBotlet`: Partner Central's neutral labels, never the reason code.
 */
internal object MatchAnsweredLabels {
    private val REJECTION_REASONS = mapOf(
        MatchRejectionReason.PRICE to "trop cher",
        MatchRejectionReason.LOCATION to "le quartier ne convient pas",
        MatchRejectionReason.SIZE to "trop petit ou trop grand",
        MatchRejectionReason.AMENITIES to "il manque des équipements",
        MatchRejectionReason.AVAILABILITY to "disponible trop tard",
        MatchRejectionReason.OTHER to "autre raison",
    )

    // null when there is nothing to say: an unknown reason (or none) leaves the line out
    fun rejectionReason(code: MatchRejectionReason?): String? = code?.let { REJECTION_REASONS[it] }
}
