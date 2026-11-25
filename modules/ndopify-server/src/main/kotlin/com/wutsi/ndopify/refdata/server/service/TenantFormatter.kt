package com.wutsi.ndopify.refdata.server.service

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** A unit [compactNumber] counts in: [shift] decimal places left of one unit, its [suffix], the decimals it shows. */
private data class CompactUnit(val shift: Int, val suffix: String, val decimals: Int)

private val COMPACT_UNITS = listOf(
    CompactUnit(3, "K", 1),
    CompactUnit(6, "M", 2),
    CompactUnit(9, "G", 2),
)
private val THOUSAND = BigDecimal(1_000)

/**
 * Formats dates, amounts and phone numbers with the tenant's rules: its patterns (`dateFormat`, `timeFormat`, `numberFormat`,
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

    /** The weekday in the tenant's first locale and time zone, lowercase, for a subject line ("samedi"). */
    fun weekday(date: Date): String =
        date.toInstant().atZone(zone)
            .format(DateTimeFormatter.ofPattern("EEEE", locale))
            .lowercase(locale)

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

    /**
     * 150K, 1,25M, 1,5G: K with one decimal at most, M and G with two, half-up, trailing zeros dropped. The unit is the
     * smallest whose rounded value reads under 1000 (999 950 → 1M, never 1000K; 999 995 000 → 1G); under 1 000 stays
     * exact and 0 reads 0. In the tenant's locale, so its decimal separator, and without any currency. Built on
     * [BigDecimal] so a boundary is exact, never a double's approximation.
     */
    fun compactNumber(amount: Long): String {
        val size = abs(amount)
        val symbols = DecimalFormatSymbols.getInstance(locale)
        if (size < 1_000) return DecimalFormat("0", symbols).format(amount)
        val magnitude = BigDecimal.valueOf(size)
        val smallest = COMPACT_UNITS.firstOrNull {
            magnitude.movePointLeft(it.shift).setScale(it.decimals, RoundingMode.HALF_UP) < THOUSAND
        }
        val unit = smallest ?: COMPACT_UNITS.last()
        val value = magnitude.movePointLeft(unit.shift).setScale(unit.decimals, RoundingMode.HALF_UP)
        val format = DecimalFormat("0." + "#".repeat(unit.decimals), symbols)
        return format.format(if (amount < 0) value.negate() else value) + unit.suffix
    }

    /**
     * A phone number in the international format (ex: `+237 6 99 00 00 01`); one without its country code is read as
     * the tenant's `countryCode`. One that can't be read is shown as entered; null when blank.
     */
    fun phone(number: String?): String? {
        val value = number?.trim()?.ifEmpty { null } ?: return null
        val util = PhoneNumberUtil.getInstance()
        return try {
            util.format(util.parse(value, tenant.countryCode.ifBlank { null }), PhoneNumberFormat.INTERNATIONAL)
        } catch (_: NumberParseException) {
            value
        }
    }

    private fun format(date: Date, pattern: String): String =
        date.toInstant().atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))

    // French groups digits with a narrow no-break space (U+202F), which e-mails don't escape: a plain space instead
    private fun number(amount: Long, pattern: String): String =
        DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale)).format(amount)
            .replace(' ', ' ')
            .replace(' ', ' ')
}
