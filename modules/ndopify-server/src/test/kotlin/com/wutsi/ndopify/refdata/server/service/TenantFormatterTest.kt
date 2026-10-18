package com.wutsi.ndopify.refdata.server.service

import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

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
    )

    // 23:30 UTC: already the next day in Douala (UTC+1)
    private val date = Date.from(Instant.parse("2026-10-16T23:30:00Z"))

    @Test
    fun `date in the tenant's format, locale and time zone`() {
        assertEquals("17 oct. 2026", TenantFormatter(tenant).date(date))
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
}
