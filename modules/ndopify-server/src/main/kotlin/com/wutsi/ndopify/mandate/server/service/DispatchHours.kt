package com.wutsi.ndopify.mandate.server.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The night of the mandate dispatch: no offer is made at night, and an offer's TTL only runs during the day. The night
 * is `[start, end)`, may span midnight, and is in the tenant's time zone, passed on each call. `start == end` means
 * no night.
 */
@Service
class DispatchHours(
    @param:Value("\${ndopify.mandate.assignment.night.start}") nightStart: String,
    @param:Value("\${ndopify.mandate.assignment.night.end}") nightEnd: String,
) {
    private val start = LocalTime.parse(nightStart)
    private val end = LocalTime.parse(nightEnd)

    fun isNight(instant: Instant, zoneId: ZoneId): Boolean {
        if (start == end) {
            return false
        }
        val time = instant.atZone(zoneId).toLocalTime()
        return if (start > end) {
            time >= start || time < end // Spans midnight: 21:00 → 07:00
        } else {
            time >= start && time < end
        }
    }

    /** [from] + [duration] counted during the day only, in [zoneId]: the clock stops at night. */
    fun addDaytime(from: Instant, duration: Duration, zoneId: ZoneId): Instant {
        if (start == end) {
            return from.plus(duration)
        }
        var cursor = from.atZone(zoneId)
        var remaining = duration
        while (true) {
            if (isNight(cursor.toInstant(), zoneId)) {
                cursor = next(cursor, end) // Morning
            }
            val night = next(cursor, start)
            val available = Duration.between(cursor, night)
            if (remaining <= available) {
                return cursor.plus(remaining).toInstant()
            }
            remaining -= available
            cursor = night
        }
    }

    // Next occurrence of [time] strictly after [from]
    private fun next(from: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val today = from.with(time)
        return if (today.isAfter(from)) today else today.plusDays(1)
    }
}
