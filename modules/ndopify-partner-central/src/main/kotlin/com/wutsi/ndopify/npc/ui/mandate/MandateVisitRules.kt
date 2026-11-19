package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.npc.ui.visit.VisitSlots
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.visit.dto.CreateMatchVisitRequest
import com.wutsi.ndopify.visit.dto.VisitType
import java.time.LocalDate
import java.time.LocalTime

/**
 * The rules of the visit page: check the accepted property, the day, the time and the type, and turn the answers into
 * the booking request. Pure functions, re-checked by ndopify-server (the slot's window included: a hand-edited date
 * outside it is its 400 `VISIT_INVALID_SLOT`, shown as `invalid-slot`).
 */
object MandateVisitRules {
    /** The types the agent may choose, in the order the page offers them. */
    val TYPES: List<Pair<VisitType, String>> = listOf(
        VisitType.ON_SITE to "Sur place",
        VisitType.VIDEO to "Visite vidéo",
    )

    /** The field errors, by form field name. An empty map when the form is valid. */
    fun errors(form: MandateVisitForm, matches: List<Match>): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        val matchId = form.matchId?.trim()?.toLongOrNull()
        if (matchId == null || matches.none { it.id == matchId }) {
            errors["matchId"] = "Choisissez le bien accepté par le client."
        }
        if (date(form) == null) {
            errors["date"] = "Choisissez un jour dans la liste."
        }
        if (time(form) == null) {
            errors["time"] = "Choisissez une heure dans la liste."
        }
        if (type(form) == null) {
            errors["type"] = "Choisissez le type de visite."
        }
        return errors
    }

    /** The booking to send: the slot's instant in the tenant's time zone, as [VisitSlots] reads it. */
    fun request(form: MandateVisitForm, tenant: Tenant): CreateMatchVisitRequest =
        CreateMatchVisitRequest(
            type = type(form) ?: VisitType.UNKNOWN,
            scheduledAt = VisitSlots.at(date(form)!!, time(form)!!, tenant),
        )

    private fun date(form: MandateVisitForm): LocalDate? =
        form.date?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun time(form: MandateVisitForm): LocalTime? =
        form.time?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    // UNKNOWN is no answer, and an unknown name (a hand-edited value) is no answer either
    private fun type(form: MandateVisitForm): VisitType? =
        form.type?.trim()?.let { runCatching { VisitType.valueOf(it) }.getOrNull() }
            ?.takeIf { candidate -> TYPES.any { it.first == candidate } }
}
