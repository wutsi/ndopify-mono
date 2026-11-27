package com.wutsi.ndopify.platform.mail.verification

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.ArrayDeque

/**
 * The cost guard of the paid SES calls: a per-IP window over the last hour and a daily global counter, both reset by a
 * restart. A call is a paid call — the service asks only on a cache miss.
 *
 * It is a budget guard, not a security control: when spent, the caller accepts the submission without verifying
 * (fail open), and a spoofed `X-Forwarded-For` only moves the per-IP bucket while the daily cap still holds.
 */
@Component
class EmailVerificationThrottle(
    private val clock: Clock,
    @param:Value("\${ndopify.mail.verification.throttle.per-ip-per-hour}") private val perIpPerHour: Int,
    @param:Value("\${ndopify.mail.verification.throttle.daily-cap}") private val dailyCap: Int,
) {
    private val windows = mutableMapOf<String, ArrayDeque<Instant>>()
    private var day: LocalDate? = null
    private var dailyCalls = 0

    @Synchronized
    fun tryAcquire(ip: String?): Boolean {
        // Read here, never in the constructor: a test can mock the Clock bean, and the day is lazily set
        val now = clock.instant()
        resetDayIfNeeded(now)
        if (dailyCalls >= dailyCap) return false

        if (ip != null) {
            val window = windows.getOrPut(ip) { ArrayDeque() }
            val since = now.minus(Duration.ofHours(1))
            while (window.isNotEmpty() && window.first().isBefore(since)) {
                window.removeFirst()
            }
            if (window.size >= perIpPerHour) return false
            window.addLast(now)
        }

        dailyCalls++
        return true
    }

    private fun resetDayIfNeeded(now: Instant) {
        val today = LocalDate.ofInstant(now, clock.zone)
        if (today != day) {
            day = today
            dailyCalls = 0
            // The per-IP windows restart with the day too: right after midnight they still hold calls of the hour
            // before, and a budget guard can afford that extra allowance
            windows.clear()
        }
    }
}
