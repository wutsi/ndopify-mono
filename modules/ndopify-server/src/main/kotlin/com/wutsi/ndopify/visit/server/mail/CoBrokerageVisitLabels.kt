package com.wutsi.ndopify.visit.server.mail

import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitType

/**
 * The French wording a co-brokerage visit's e-mail and Telegram message share — the cancellation's actor, the
 * no-show's reason and the visit's type — so that the two channels can't drift.
 */
internal object CoBrokerageVisitLabels {
    const val CANCELLED_BY_AGENT = "J'ai annulé la visite."
    const val CANCELLED_BY_CUSTOMER = "Le client a annulé la visite."
    const val CANCELLED_BY_SYSTEM = "La visite est annulée."
    const val CUSTOMER_ABSENT = "Le client ne s'est pas présenté à la visite."
    const val CODE_REFUSED = "Le client a refusé de donner le code de visite."

    fun cancelledBy(party: VisitParty?): String = when (party) {
        VisitParty.AGENT -> CANCELLED_BY_AGENT
        VisitParty.CUSTOMER -> CANCELLED_BY_CUSTOMER
        else -> CANCELLED_BY_SYSTEM
    }

    fun noShowReason(reason: VisitNoShowReason?): String =
        if (reason == VisitNoShowReason.CODE_REFUSED) CODE_REFUSED else CUSTOMER_ABSENT

    fun typeLabel(type: VisitType): String = if (type == VisitType.ON_SITE) "Sur place" else "Visite vidéo"
}
