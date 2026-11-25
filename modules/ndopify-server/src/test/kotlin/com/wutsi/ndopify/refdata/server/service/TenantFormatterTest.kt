package com.wutsi.ndopify.refdata.server.service

import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TenantFormatterTest {
    private val tenant = TenantEntity(
        id = 1L,
        locales = listOf("fr-CM", "en-CM"),
        currencyCode = "XAF",
        numberFormat = "#,###,##0.00",
        monetaryFormat = "#,###,##0 FCFA",
        dateFormat = "dd MMM yyyy",
        timeFormat = "HH:mm",
        timeZone = "Africa/Douala",
        countryCode = "CM",
    )

    // 23:30 UTC: already the next day in Douala (UTC+1)
    private val date = Date.from(Instant.parse("2026-10-16T23:30:00Z"))

    @Test
    fun `date in the tenant's format, locale and time zone`() {
        assertEquals("17 oct. 2026", TenantFormatter(tenant).date(date))
    }

    @Test
    fun `calendar date in the tenant's format and locale`() {
        assertEquals("16 oct. 2026", TenantFormatter(tenant).date(LocalDate.parse("2026-10-16")))
    }

    @Test
    fun `date in the tenant's first locale`() {
        assertEquals("17 Oct 2026", TenantFormatter(tenant.copy(locales = listOf("en-CM"))).date(date))
    }

    @Test
    fun `time in the tenant's format and time zone`() {
        assertEquals("00:30", TenantFormatter(tenant).time(date))
        assertEquals("19:30", TenantFormatter(tenant.copy(timeZone = "America/Toronto")).time(date))
    }

    @Test
    fun `weekday in the first locale and time zone`() {
        // 23:30 UTC on Friday: already Saturday in Douala
        assertEquals("samedi", TenantFormatter(tenant).weekday(date))
        assertEquals("saturday", TenantFormatter(tenant.copy(locales = listOf("en-CM"))).weekday(date))
        assertEquals(
            "friday",
            TenantFormatter(tenant.copy(locales = listOf("en-CM"), timeZone = "America/Toronto")).weekday(date),
        )
    }

    @Test
    fun `money in the tenant's currency`() {
        assertEquals("1 500 000 FCFA", TenantFormatter(tenant).money(1500000, "XAF"))
    }

    @Test
    fun `money in another currency`() {
        assertEquals("1 500 000,00 EUR", TenantFormatter(tenant).money(1500000, "EUR"))
    }

    @Test
    fun `blank formats - defaults`() {
        val formatter = TenantFormatter(TenantEntity(id = 1L, currencyCode = "XAF"))

        assertEquals("17/10/2026", formatter.date(date))
        assertEquals("00:30", formatter.time(date))
        assertEquals("1 500 000 XAF", formatter.money(1500000, "XAF"))
    }

    @Test
    fun `phone in the international format`() {
        assertEquals("+237 6 99 00 00 01", TenantFormatter(tenant).phone("+237699000001"))
    }

    @Test
    fun `phone without country code - the tenant's`() {
        assertEquals("+237 6 99 00 00 01", TenantFormatter(tenant).phone("699000001"))
    }

    @Test
    fun `phone of another country`() {
        assertEquals("+1 514-555-0101", TenantFormatter(tenant).phone("+15145550101"))
    }

    @Test
    fun `phone that can't be read - as entered`() {
        assertEquals("abc", TenantFormatter(tenant).phone(" abc "))
    }

    @Test
    fun `phone blank - null`() {
        assertNull(TenantFormatter(tenant).phone(" "))
        assertNull(TenantFormatter(tenant).phone(null))
    }

    @Test
    fun `compact number - the smallest unit that reads under 1000`() {
        val formatter = TenantFormatter(tenant)

        assertEquals("0", formatter.compactNumber(0))
        assertEquals("999", formatter.compactNumber(999))
        assertEquals("500K", formatter.compactNumber(500_000))
        assertEquals("1,5M", formatter.compactNumber(1_500_000))
        assertEquals("25M", formatter.compactNumber(25_000_000))
        assertEquals("1M", formatter.compactNumber(999_950))
        assertEquals("1G", formatter.compactNumber(999_995_000))
        assertEquals("1,25G", formatter.compactNumber(1_250_000_000))
    }

    @Test
    fun `compact number follows the tenant's decimal separator`() {
        assertEquals("1.5M", TenantFormatter(tenant.copy(locales = listOf("en-CM"))).compactNumber(1_500_000))
    }
}
