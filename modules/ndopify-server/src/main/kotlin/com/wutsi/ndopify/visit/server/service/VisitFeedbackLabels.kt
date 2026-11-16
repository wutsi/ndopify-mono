package com.wutsi.ndopify.visit.server.service

import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.visit.dto.VisitInterest

/**
 * The French wording of the visit feedback notification — the customer's answer and, when the property doesn't interest
 * it, the reason — shared by `VisitFeedbackBotlet` and `VisitFeedbackMailet` so that the two channels can't drift.
 */
internal object VisitFeedbackLabels {
    fun interest(interest: VisitInterest): String =
        if (interest == VisitInterest.NOT_INTERESTED) "Pas intéressé" else "Intéressé"

    /** The reason as the match popup's labels word it, null for none / UNKNOWN. */
    fun reason(code: MatchRejectionReason?): String? = when (code) {
        MatchRejectionReason.PRICE -> "trop cher"
        MatchRejectionReason.LOCATION -> "le quartier ne convient pas"
        MatchRejectionReason.SIZE -> "trop petit ou trop grand"
        MatchRejectionReason.AMENITIES -> "il manque des équipements"
        MatchRejectionReason.AVAILABILITY -> "disponible trop tard"
        MatchRejectionReason.OTHER -> "autre raison"
        else -> null
    }
}
