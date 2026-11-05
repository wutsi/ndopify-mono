package com.wutsi.ndopify.web.ui.visit

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.core.service.TenantFormatter
import com.wutsi.ndopify.web.ui.listing.VisitDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/**
 * The visit slots offered to a customer: the same days and times on the listing popup and the match page. Days and
 * labels are formatted in the tenant's locale and time zone, never with a hard-coded pattern.
 */
object VisitSlots {
    private const val DAYS = 7
    private val FIRST_TIME = LocalTime.of(8, 0)
    private val LAST_TIME = LocalTime.of(20, 0)

    /** The 7 days starting tomorrow, labelled like the listing popup's days. */
    fun days(today: LocalDate, formatter: TenantFormatter): List<VisitDay> = (1..DAYS).map { today.plusDays(it.toLong()) }.map { day ->
        VisitDay(
            value = day.toString(),
            dayName = formatter.date(day, "EEE"),
            day = day.dayOfMonth.toString(),
            month = formatter.date(day, "MMM"),
        )
    }

    /** 08:00 to 20:00 every 30 minutes as ("08:00", "8h00") … ("20:00", "20h00"). */
    fun times(): List<Pair<String, String>> = generateSequence(FIRST_TIME) { it.plusMinutes(30) }
        .takeWhile { !it.isAfter(LAST_TIME) }
        .map { time -> time.toString() to "%dh%02d".format(time.hour, time.minute) }
        .toList()

    /** The instant of a tenant-local date and time, for CreateVisitRequest.requestedAt. */
    fun requestedAt(date: LocalDate, time: LocalTime, tenant: Tenant): Date =
        Date.from(LocalDateTime.of(date, time).atZone(ZoneId.of(tenant.timeZone)).toInstant())

    /** "Sur place · samedi 7 novembre 2026 à 10:30": the long date in the tenant's locale, its time format. */
    fun label(typeLabel: String, at: Date, tenant: Tenant, formatter: TenantFormatter): String {
        val local = at.toInstant().atZone(ZoneId.of(tenant.timeZone)).toLocalDate()
        return "$typeLabel · ${formatter.date(local, "EEEE d MMMM yyyy")} à ${formatter.time(at)}"
    }
}
