package com.wutsi.ndopify.web.core.service

import com.wutsi.ndopify.refdata.dto.Tenant
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** A unit a short amount counts in: [shift] decimal places left of one unit, its [suffix], the decimals it shows. */
private data class CompactUnit(val shift: Int, val suffix: String, val decimals: Int)

private val COMPACT_UNITS = listOf(
    CompactUnit(3, "K", 1),
    CompactUnit(6, "M", 2),
    CompactUnit(9, "G", 2),
)
private val THOUSAND = BigDecimal(1_000)

/**
 * Formats dates and amounts with the tenant's rules, as ndopify-server's `TenantFormatter` does in its e-mails: its
 * patterns (`dateFormat`, `timeFormat`, `numberFormat`), its first locale (month names, separators, decimal point) and
 * its `timeZone` (dates as its users read them). An amount is shown short ("150K FCFA", [compactMoney]), followed by
 * the tenant's currency. A blank pattern falls back to a default.
 */
class TenantFormatter(private val tenant: Tenant) {
    companion object {
        const val DEFAULT_DATE_FORMAT = "dd/MM/yyyy"
        const val DEFAULT_TIME_FORMAT = "HH:mm"
        const val DEFAULT_NUMBER_FORMAT = "#,##0"
    }

    private val locale = tenant.locales.firstOrNull()?.ifBlank { null }?.let { Locale.forLanguageTag(it.trim()) }
        ?: Locale.FRENCH
    private val zone = ZoneId.of(tenant.timeZone)

    fun date(date: Date): String =
        date.toInstant().atZone(zone).format(dateFormatter())

    fun date(date: LocalDate): String = date.format(dateFormatter())

    /** [date] with [pattern] in the tenant's locale ("sam.", "oct."), for what its `dateFormat` doesn't say. */
    fun date(date: LocalDate, pattern: String): String = date.format(DateTimeFormatter.ofPattern(pattern, locale))

    /** [date] in the tenant's time zone, with [pattern] and its first locale ("octobre 2026"). */
    fun date(date: Date, pattern: String): String =
        date.toInstant().atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))

    fun time(date: Date): String =
        date.toInstant().atZone(zone)
            .format(DateTimeFormatter.ofPattern(tenant.timeFormat.ifBlank { DEFAULT_TIME_FORMAT }, locale))

    /** Today's date in the tenant's time zone. */
    fun today(clock: Clock): LocalDate = LocalDate.now(clock.withZone(zone))

    private fun dateFormatter() = DateTimeFormatter.ofPattern(tenant.dateFormat.ifBlank { DEFAULT_DATE_FORMAT }, locale)

    /** The tenant's currency as its users read it ("FCFA"), for a field's unit or a message. */
    fun currency(): String = tenant.currencySymbol.ifBlank { tenant.currencyCode }

    /**
     * The tenant's decimal separator ("," in French, "." in English), for a page script that formats an amount in the
     * browser exactly as [compactMoney] does. Emitted on the page as `data-decimal-separator`.
     */
    fun decimalSeparator(): String = DecimalFormatSymbols.getInstance(locale).decimalSeparator.toString()

    /**
     * An amount in short, for a card ("80K FCFA", "152,5K FCFA", "1,25M FCFA", "1,5G FCFA"): in the tenant's locale,
     * followed by its currency. See [compactNumber] for the unit and its decimals.
     */
    fun compactMoney(amount: Long, currencyCode: String = tenant.currencyCode): String {
        val currency = if (currencyCode == tenant.currencyCode) currency() else currencyCode
        return listOf(compactNumber(amount), currency).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * 150K, 1,25M, 1,5G: K with one decimal at most, M and G with two, half-up, trailing zeros dropped. The unit is the
     * smallest whose rounded value reads under 1000 (999 950 → 1M, never 1000K; 999 995 000 → 1G); under 1 000 stays
     * exact and 0 reads 0. Built on [BigDecimal] so a boundary is exact, never a double's approximation.
     */
    private fun compactNumber(amount: Long): String {
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

    /** A count in the tenant's `numberFormat`, without decimals ("1 200", for an area in m²). */
    fun count(value: Long): String {
        val format = DecimalFormat(tenant.numberFormat.ifBlank { DEFAULT_NUMBER_FORMAT }, DecimalFormatSymbols.getInstance(locale))
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = 0
        return format.format(value).replace(' ', ' ').replace(' ', ' ')
    }
}
