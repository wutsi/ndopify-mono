package com.wutsi.ndopify.visit.server.service

import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import java.time.ZoneId
import java.util.Date

/**
 * The slot a customer may ask for, and an agent may schedule: a date and time on one of the 7 days starting tomorrow,
 * 08:00-20:00 in the tenant's time zone, on a 30-minute boundary. Pure: the caller passes the tenant and the current
 * time.
 */
object VisitSlots {
    private const val FIRST_HOUR = 8
    private const val LAST_HOUR = 20
    private const val MINUTE_STEP = 30

    fun isValid(at: Date, tenant: TenantEntity, now: Date): Boolean {
        val zone = ZoneId.of(tenant.timeZone)
        val slot = at.toInstant().atZone(zone)
        val today = now.toInstant().atZone(zone).toLocalDate()

        val date = slot.toLocalDate()
        if (date.isBefore(today.plusDays(1)) || date.isAfter(today.plusDays(7))) {
            return false
        }

        val time = slot.toLocalTime()
        if (time.hour < FIRST_HOUR || time.hour > LAST_HOUR) {
            return false
        }
        if (time.hour == LAST_HOUR && time.minute != 0) {
            return false
        }
        return time.minute % MINUTE_STEP == 0 && time.second == 0 && time.nano == 0
    }
}
