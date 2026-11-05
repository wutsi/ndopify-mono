package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/** A day of the schedule form: the value it posts (`2026-11-07`) and the label it shows. */
data class VisitDay(val value: String, val label: String)

/**
 * The slots of a visit, in the tenant's time zone: the 7 days starting tomorrow, 08:00 through 20:00 every 30 minutes.
 * Same rules as ndopify-server's `VisitSlots`.
 */
object VisitSlots {
    private const val FIRST_MINUTE = 8 * 60
    private const val LAST_MINUTE = 20 * 60
    private const val STEP = 30

    private const val DAY_FORMAT = "EEE d MMMM yyyy"
    private const val LONG_DAY_FORMAT = "EEEE d MMMM yyyy"

    /** The 7 days starting the day after [today]. */
    fun days(today: LocalDate, formatter: TenantFormatter): List<VisitDay> =
        (1..7).map { offset ->
            val date = today.plusDays(offset.toLong())
            VisitDay(date.toString(), formatter.date(date, DAY_FORMAT))
        }

    /** The 25 times of a day, `08:00` posted and `8h00` shown. */
    fun times(): List<Pair<String, String>> =
        (FIRST_MINUTE..LAST_MINUTE step STEP).map { minute ->
            val time = LocalTime.of(minute / 60, minute % 60)
            time.toString() to "%dh%02d".format(time.hour, time.minute)
        }

    /** The instant of [date] at [time] in the tenant's time zone. */
    fun at(date: LocalDate, time: LocalTime, tenant: Tenant): Date =
        Date.from(LocalDateTime.of(date, time).atZone(zone(tenant)).toInstant())

    fun localDate(at: Date, tenant: Tenant): LocalDate = at.toInstant().atZone(zone(tenant)).toLocalDate()

    fun localTime(at: Date, tenant: Tenant): LocalTime = at.toInstant().atZone(zone(tenant)).toLocalTime()

    /** "sam. 7 novembre 2026 à 10:30", without the type. */
    fun dateTimeLabel(at: Date, tenant: Tenant, formatter: TenantFormatter): String =
        "${formatter.date(localDate(at, tenant), DAY_FORMAT)} à ${formatter.time(at)}"

    /** "Sur place · samedi 7 novembre 2026 à 10:30", with the full day name. */
    fun label(typeLabel: String, at: Date, tenant: Tenant, formatter: TenantFormatter): String =
        "$typeLabel · ${formatter.date(localDate(at, tenant), LONG_DAY_FORMAT)} à ${formatter.time(at)}"

    private fun zone(tenant: Tenant): ZoneId = ZoneId.of(tenant.timeZone)
}
