package com.wutsi.ndopify.web.ui.match

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.visit.dto.CreateVisitRequest
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.ui.visit.VisitSlots
import java.time.LocalDate
import java.time.LocalTime

/**
 * The "demander une visite" form of the match page: a [type] (`ON_SITE` / `VIDEO`), a date and a time picked on one
 * of the offered slots, and an optional [note].
 */
data class VisitRequestForm(
    val type: String? = null,
    val visitDate: String? = null,
    val visitTime: String? = null,
    val note: String? = null,
) {
    /**
     * The request to post, or null when the form is not usable: an unknown (or UNKNOWN) type, or a date/time that is
     * missing or not parseable. The note is trimmed (blank → none) and cut to [CreateVisitRequest.note]'s size; the
     * instant is the tenant-local date/time, as the customer picked it.
     */
    fun toRequest(matchId: Long, tenant: Tenant): CreateVisitRequest? {
        val visitType = VisitType.entries.find { it.name == type }?.takeIf { it != VisitType.UNKNOWN } ?: return null
        val date = visitDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val time = visitTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
        return CreateVisitRequest(
            matchId = matchId,
            type = visitType,
            requestedAt = VisitSlots.requestedAt(date, time, tenant),
            note = note?.trim()?.ifEmpty { null }?.take(MAX_NOTE),
        )
    }

    companion object {
        /** `CreateVisitRequest.note`'s size on ndopify-server: the form allows more, a longer note is cut. */
        private const val MAX_NOTE = 500
    }
}
