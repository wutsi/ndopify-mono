package com.wutsi.ndopify.mandate.server.service

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DispatchHoursTest {
    private val douala = ZoneId.of("Africa/Douala") // UTC+1, no DST
    private val toronto = ZoneId.of("America/Toronto")
    private val hours = DispatchHours("21:00", "07:00")

    private fun at(time: String, zone: ZoneId = douala): Instant =
        LocalDateTime.parse("2026-10-15T$time").atZone(zone).toInstant()

    private fun on(date: String, zone: ZoneId = douala): Instant = LocalDateTime.parse(date).atZone(zone).toInstant()

    // isNight

    @Test
    fun `day before the night starts`() = assertFalse(hours.isNight(at("20:59"), douala))

    @Test
    fun `night starts at night start`() = assertTrue(hours.isNight(at("21:00"), douala))

    @Test
    fun `midnight is night`() = assertTrue(hours.isNight(at("00:00"), douala))

    @Test
    fun `night until night end`() = assertTrue(hours.isNight(at("06:59"), douala))

    @Test
    fun `day starts at night end`() = assertFalse(hours.isNight(at("07:00"), douala))

    @Test
    fun `noon is day`() = assertFalse(hours.isNight(at("12:00"), douala))

    @Test
    fun `window not spanning midnight`() {
        val siesta = DispatchHours("12:00", "14:00")

        assertFalse(siesta.isNight(at("11:59"), douala))
        assertTrue(siesta.isNight(at("12:00"), douala))
        assertTrue(siesta.isNight(at("13:59"), douala))
        assertFalse(siesta.isNight(at("14:00"), douala))
        assertFalse(siesta.isNight(at("23:00"), douala))
    }

    @Test
    fun `same start and end - never night`() {
        val always = DispatchHours("21:00", "21:00")

        assertFalse(always.isNight(at("21:00"), douala))
        assertFalse(always.isNight(at("03:00"), douala))
    }

    @Test
    fun `the tenant's time zone decides`() {
        val instant = Instant.parse("2026-10-15T20:30:00Z") // 21:30 in Douala, 16:30 in Toronto

        assertTrue(hours.isNight(instant, douala))
        assertFalse(hours.isNight(instant, toronto))
    }

    // addDaytime

    @Test
    fun `during the day - plain addition`() =
        assertEquals(at("11:00"), hours.addDaytime(at("10:00"), Duration.ofHours(1), douala))

    @Test
    fun `ending exactly when the night starts`() =
        assertEquals(at("21:00"), hours.addDaytime(at("20:00"), Duration.ofHours(1), douala))

    @Test
    fun `crossing the night - the clock stops`() =
        assertEquals(on("2026-10-16T07:30"), hours.addDaytime(at("20:30"), Duration.ofHours(1), douala))

    @Test
    fun `starting at night - counts from the morning`() =
        assertEquals(on("2026-10-16T08:00"), hours.addDaytime(at("23:00"), Duration.ofHours(1), douala))

    @Test
    fun `starting after midnight - counts from the same morning`() =
        assertEquals(on("2026-10-15T08:00"), hours.addDaytime(at("03:00"), Duration.ofHours(1), douala))

    @Test
    fun `spanning 2 nights`() {
        // 20:00 → 21:00 (1h), 07:00 → 21:00 next day (14h), then 07:00 → 08:00 the day after (1h)
        assertEquals(on("2026-10-17T08:00"), hours.addDaytime(at("20:00"), Duration.ofHours(16), douala))
    }

    @Test
    fun `window not spanning midnight - the clock stops during it`() {
        val siesta = DispatchHours("12:00", "14:00")

        assertEquals(at("14:30"), siesta.addDaytime(at("11:30"), Duration.ofHours(1), douala))
    }

    @Test
    fun `same start and end - plain addition`() {
        val always = DispatchHours("21:00", "21:00")

        assertEquals(on("2026-10-15T23:00"), always.addDaytime(at("20:30"), Duration.ofMinutes(150), douala))
    }

    @Test
    fun `in the tenant's time zone`() {
        // 20:30 in Toronto: 30 min left before its night, then its morning
        val from = at("20:30", toronto)

        assertEquals(on("2026-10-16T07:30", toronto), hours.addDaytime(from, Duration.ofHours(1), toronto))
    }
}
