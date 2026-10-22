package com.wutsi.ndopify.refdata.server.service

import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Formats dates and amounts with the tenant's rules: its patterns (`dateFormat`, `timeFormat`, `numberFormat`,
 * `monetaryFormat`), its first locale (month names, separators) and its `timeZone` (dates as its users read them).
 * A blank pattern falls back to a default.
 */
class TenantFormatter(private val tenant: TenantEntity) {
    companion object {
        const val DEFAULT_DATE_FORMAT = "dd/MM/yyyy"
        const val DEFAULT_TIME_FORMAT = "HH:mm"
        const val DEFAULT_NUMBER_FORMAT = "#,##0"
    }

    private val locale = tenant.locales.firstOrNull()?.ifBlank { null }?.let { Locale.forLanguageTag(it.trim()) }
        ?: Locale.FRENCH
    private val zone = ZoneId.of(tenant.timeZone)

    fun date(date: Date): String = format(date, tenant.dateFormat.ifBlank { DEFAULT_DATE_FORMAT })

    // A calendar date (ex: a listing's availability): no time zone to apply
    fun date(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern(tenant.dateFormat.ifBlank { DEFAULT_DATE_FORMAT }, locale))

    fun time(date: Date): String = format(date, tenant.timeFormat.ifBlank { DEFAULT_TIME_FORMAT })

    /**
     * The tenant's `monetaryFormat` holds its currency (ex: `#,###,##0 FCFA`): an amount in another currency gets the
     * `numberFormat` followed by its currency code.
     */
    fun money(amount: Long, currencyCode: String): String =
        if (currencyCode == tenant.currencyCode && tenant.monetaryFormat.isNotBlank()) {
            number(amount, tenant.monetaryFormat)
        } else {
            "${number(amount, tenant.numberFormat.ifBlank { DEFAULT_NUMBER_FORMAT })} $currencyCode"
        }

    private fun format(date: Date, pattern: String): String =
        date.toInstant().atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))

    // French groups digits with a narrow no-break space (U+202F), which e-mails don't escape: a plain space instead
    private fun number(amount: Long, pattern: String): String =
        DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale)).format(amount)
            .replace(' ', ' ')
            .replace(' ', ' ')
}
