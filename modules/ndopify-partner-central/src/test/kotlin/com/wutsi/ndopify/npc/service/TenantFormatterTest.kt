package com.wutsi.ndopify.npc.service

import com.wutsi.ndopify.npc.service.TenantFixtures.TENANT
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class TenantFormatterTest {
    // 23:30 UTC: already the next day in Douala (UTC+1)
    private val date = Date.from(Instant.parse("2026-10-16T23:30:00Z"))

    @Test
    fun `date in the tenant's format, locale and time zone`() {
        assertEquals("17 oct. 2026", TenantFormatter(TENANT).date(date))
        assertEquals("16 oct. 2026", TenantFormatter(TENANT.copy(timeZone = "America/Toronto")).date(date))
    }

    @Test
    fun `local date in the tenant's format and locale`() {
        assertEquals("03 nov. 2026", TenantFormatter(TENANT).date(LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun `today in the tenant's time zone`() {
        val clock = Clock.fixed(Instant.parse("2026-10-21T23:30:00Z"), java.time.ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 10, 22), TenantFormatter(TENANT).today(clock))
    }

    @Test
    fun `date in the tenant's first locale`() {
        assertEquals("17 Oct 2026", TenantFormatter(TENANT.copy(locales = listOf("en-CM"))).date(date))
    }

    @Test
    fun `date without a format`() {
        assertEquals("17/10/2026", TenantFormatter(TENANT.copy(dateFormat = "")).date(date))
    }

    @Test
    fun `amount in the tenant's monetary format, never wrapping`() {
        assertEquals("1 500 000 FCFA", TenantFormatter(TENANT).money(1_500_000))
        assertEquals("1 500 000 FCFA", TenantFormatter(TENANT).money(1_500_000, "XAF"))
    }

    @Test
    fun `amount in another currency`() {
        assertEquals("1,500.00 EUR", TenantFormatter(TENANT.copy(locales = listOf("en"))).money(1500, "EUR"))
    }

    @Test
    fun `amount without a monetary format`() {
        val tenant = TENANT.copy(monetaryFormat = "", numberFormat = "")
        assertEquals("150 000 FCFA", TenantFormatter(tenant).money(150_000))
        assertEquals("150 000 XAF", TenantFormatter(tenant.copy(currencySymbol = "")).money(150_000))
        assertEquals("150 000", TenantFormatter(tenant.copy(currencySymbol = "", currencyCode = "")).money(150_000))
    }

    @Test
    fun `amount in short`() {
        val formatter = TenantFormatter(TENANT)
        assertEquals("80K\u00a0FCFA", formatter.compactMoney(80_000))
        assertEquals("150K\u00a0FCFA", formatter.compactMoney(150_000))
        assertEquals("152,5K\u00a0FCFA", formatter.compactMoney(152_500))
        assertEquals("1,25M\u00a0FCFA", formatter.compactMoney(1_250_000))
        assertEquals("2M\u00a0FCFA", formatter.compactMoney(2_000_000))
        assertEquals("500\u00a0FCFA", formatter.compactMoney(500))
        assertEquals("1.5K\u00a0EUR", TenantFormatter(TENANT.copy(locales = listOf("en"))).compactMoney(1500, "EUR"))
    }

    @Test
    fun currency() {
        assertEquals("FCFA", TenantFormatter(TENANT).currency())
        assertEquals("XAF", TenantFormatter(TENANT.copy(currencySymbol = "")).currency())
    }
}
