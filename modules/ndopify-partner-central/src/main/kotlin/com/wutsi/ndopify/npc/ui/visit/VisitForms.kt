package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.visit.dto.CancelVisitRequest
import com.wutsi.ndopify.visit.dto.CheckInVisitRequest
import com.wutsi.ndopify.visit.dto.CheckOutVisitRequest
import com.wutsi.ndopify.visit.dto.NoShowVisitRequest
import com.wutsi.ndopify.visit.dto.ScheduleVisitRequest
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitType
import java.time.LocalDate
import java.time.LocalTime

/** The schedule form: a day and a time from the lists, and a type. A hand-edited value that can't be parsed is null. */
data class VisitScheduleForm(val date: String? = null, val time: String? = null, val type: String? = null) {
    fun toRequest(tenant: Tenant, fallback: VisitType): ScheduleVisitRequest? {
        val date = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val time = time?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
        val type = VisitType.entries.find { it.name == this.type }
            ?.takeIf { it == VisitType.ON_SITE || it == VisitType.VIDEO }
            ?: fallback
        return ScheduleVisitRequest(VisitSlots.at(date, time, tenant), type)
    }
}

/** The refuse / cancel form: an optional reason, cut to the server's 500 characters. */
data class VisitCancelForm(val reason: String? = null) {
    fun toRequest(): CancelVisitRequest = CancelVisitRequest(reason?.trim()?.take(MAX)?.ifEmpty { null })

    private companion object {
        const val MAX = 500
    }
}

/** The check-in form: the customer's 4-digit code and an optional position. */
data class VisitCheckInForm(
    val code: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val accuracyMeters: String? = null,
) {
    fun toRequest(): CheckInVisitRequest? {
        val code = code?.trim() ?: return null
        if (!CODE.matches(code)) return null
        return CheckInVisitRequest(code, number(latitude), number(longitude), number(accuracyMeters))
    }

    private companion object {
        val CODE = Regex("""^\d{4}$""")
    }
}

/** The no-show form: why the visit didn't happen, and an optional position. */
data class VisitNoShowForm(
    val reason: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val accuracyMeters: String? = null,
) {
    fun toRequest(): NoShowVisitRequest? {
        val reason = when (reason) {
            VisitNoShowReason.CUSTOMER_ABSENT.name -> VisitNoShowReason.CUSTOMER_ABSENT
            VisitNoShowReason.CODE_REFUSED.name -> VisitNoShowReason.CODE_REFUSED
            else -> return null
        }
        return NoShowVisitRequest(reason, number(latitude), number(longitude), number(accuracyMeters))
    }
}

/** The check-out form: the agent's private report and an optional position. */
data class VisitCheckOutForm(
    val comment: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val accuracyMeters: String? = null,
) {
    fun toRequest(): CheckOutVisitRequest? {
        val comment = comment?.trim() ?: return null
        if (comment.isEmpty() || comment.length > MAX) return null
        return CheckOutVisitRequest(comment, number(latitude), number(longitude), number(accuracyMeters))
    }

    private companion object {
        const val MAX = 2000
    }
}

/** A blank or malformed position is null: it is never required and never fails the action. */
private fun number(value: String?): Double? = value?.trim()?.toDoubleOrNull()
