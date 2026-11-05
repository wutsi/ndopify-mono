package com.wutsi.ndopify.web.ui.visit

import com.wutsi.ndopify.web.service.TenantFixtures.FORMATTER
import com.wutsi.ndopify.web.service.TenantFixtures.TENANT
import com.wutsi.ndopify.web.ui.listing.VisitDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisitSlotsTest {
    @Test
    fun days() {
        val days = VisitSlots.days(LocalDate.of(2026, 10, 31), FORMATTER)

        assertEquals(7, days.size)
        assertEquals(VisitDay("2026-11-01", "dim.", "1", "nov."), days.first())
        assertEquals("2026-11-07", days.last().value)
    }

    @Test
    fun times() {
        val times = VisitSlots.times()

        assertEquals(25, times.size)
        assertEquals("08:00" to "8h00", times.first())
        assertEquals("20:00" to "20h00", times.last())
        assertTrue(times.none { it.first == "20:30" })
    }

    @Test
    fun requestedAt() {
        assertEquals(
            Date.from(Instant.parse("2026-11-01T09:00:00Z")),
            VisitSlots.requestedAt(LocalDate.of(2026, 11, 1), LocalTime.of(10, 0), TENANT),
        )
    }

    @Test
    fun label() {
        assertEquals(
            "Sur place · samedi 7 novembre 2026 à 10:30",
            VisitSlots.label("Sur place", Date.from(Instant.parse("2026-11-07T09:30:00Z")), TENANT, FORMATTER),
        )
    }
}
