package com.wutsi.ndopify.esignature.service

import com.wutsi.ndopify.esignature.service.TenantFixtures.TENANT
import java.time.Instant
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
    fun `date in the tenant's first locale`() {
        assertEquals("17 Oct 2026", TenantFormatter(TENANT.copy(locales = listOf("en-CM"))).date(date))
    }

    @Test
    fun `date without a format`() {
        assertEquals("17/10/2026", TenantFormatter(TENANT.copy(dateFormat = "")).date(date))
    }

    @Test
    fun `time in the tenant's format and time zone`() {
        assertEquals("00:30", TenantFormatter(TENANT).time(date))
        assertEquals("12:30 AM", TenantFormatter(TENANT.copy(timeFormat = "hh:mm a", locales = listOf("en"))).time(date))
    }

    @Test
    fun `time without a format`() {
        assertEquals("00:30", TenantFormatter(TENANT.copy(timeFormat = "")).time(date))
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
    fun currency() {
        assertEquals("FCFA", TenantFormatter(TENANT).currency())
        assertEquals("XAF", TenantFormatter(TENANT.copy(currencySymbol = "")).currency())
    }
}
