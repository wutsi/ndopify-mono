package com.wutsi.ndopify.visit.server.service

import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitSlotsTest {
    private val zone = ZoneId.of("Africa/Douala")
    private val tenant = TenantEntity(id = 1L, timeZone = "Africa/Douala")
    private val now = date(2026, 11, 3, 12, 0) // A Tuesday

    @Test
    fun `tomorrow at 0800 is valid`() {
        assertTrue(VisitSlots.isValid(date(2026, 11, 4, 8, 0), tenant, now))
    }

    @Test
    fun `today at 2000 is not`() {
        assertFalse(VisitSlots.isValid(date(2026, 11, 3, 20, 0), tenant, now))
    }

    @Test
    fun `the seventh day at 2000 is valid`() {
        assertTrue(VisitSlots.isValid(date(2026, 11, 10, 20, 0), tenant, now))
    }

    @Test
    fun `the eighth day is not`() {
        assertFalse(VisitSlots.isValid(date(2026, 11, 11, 8, 0), tenant, now))
    }

    @Test
    fun `2030 is not`() {
        assertFalse(VisitSlots.isValid(date(2026, 11, 4, 20, 30), tenant, now))
    }

    @Test
    fun `1015 is not`() {
        assertFalse(VisitSlots.isValid(date(2026, 11, 4, 10, 15), tenant, now))
    }

    @Test
    fun `0730 is not`() {
        assertFalse(VisitSlots.isValid(date(2026, 11, 4, 7, 30), tenant, now))
    }

    @Test
    fun `2100 is not`() {
        assertFalse(VisitSlots.isValid(date(2026, 11, 4, 21, 0), tenant, now))
    }

    @Test
    fun `1030 is valid`() {
        assertTrue(VisitSlots.isValid(date(2026, 11, 4, 10, 30), tenant, now))
    }

    @Test
    fun `a second after the boundary is not`() {
        assertFalse(VisitSlots.isValid(date(2026, 11, 4, 10, 0, 1), tenant, now))
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Date =
        Date.from(
            LocalDateTime.of(LocalDate.of(year, month, day), java.time.LocalTime.of(hour, minute, second))
                .atZone(zone)
                .toInstant(),
        )
}
