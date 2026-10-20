package com.wutsi.ndopify.npc.service

import com.wutsi.ndopify.refdata.dto.Tenant
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Formats dates and amounts with the tenant's rules, as ndopify-server's `TenantFormatter` does in its e-mails: its
 * patterns (`dateFormat`, `numberFormat`, `monetaryFormat`), its first locale (month names, separators) and its
 * `timeZone` (dates as its users read them). A blank pattern falls back to a default.
 */
class TenantFormatter(private val tenant: Tenant) {
    companion object {
        const val DEFAULT_DATE_FORMAT = "dd/MM/yyyy"
        const val DEFAULT_NUMBER_FORMAT = "#,##0"
    }

    private val locale = tenant.locales.firstOrNull()?.ifBlank { null }?.let { Locale.forLanguageTag(it.trim()) }
        ?: Locale.FRENCH
    private val zone = ZoneId.of(tenant.timeZone)

    fun date(date: Date): String =
        date.toInstant().atZone(zone).format(DateTimeFormatter.ofPattern(tenant.dateFormat.ifBlank { DEFAULT_DATE_FORMAT }, locale))

    /** The tenant's currency as its users read it ("FCFA"), for a field's unit or a message. */
    fun currency(): String = tenant.currencySymbol.ifBlank { tenant.currencyCode }

    /**
     * The tenant's `monetaryFormat` holds its currency (ex: `#,###,##0 FCFA`): an amount in another currency, or
     * without that format, gets the `numberFormat` followed by its currency.
     */
    fun money(amount: Long, currencyCode: String = tenant.currencyCode): String =
        if (currencyCode == tenant.currencyCode && tenant.monetaryFormat.isNotBlank()) {
            number(amount, tenant.monetaryFormat)
        } else {
            val currency = if (currencyCode == tenant.currencyCode) currency() else currencyCode
            listOf(number(amount, tenant.numberFormat.ifBlank { DEFAULT_NUMBER_FORMAT }), currency)
                .filter { it.isNotEmpty() }
                .joinToString("\u00A0")
        }

    /**
     * An amount in short, for a card ("80K FCFA", "152,5K FCFA", "1,25M FCFA"): thousands as K (one decimal at most),
     * millions as M (two at most), in the tenant's locale, followed by its currency.
     */
    fun compactMoney(amount: Long, currencyCode: String = tenant.currencyCode): String {
        val (value, suffix, decimals) = when {
            abs(amount) >= 1_000_000 -> Triple(amount / 1_000_000.0, "M", "##")
            abs(amount) >= 1_000 -> Triple(amount / 1_000.0, "K", "#")
            else -> Triple(amount.toDouble(), "", "")
        }
        val format = DecimalFormat("0" + if (decimals.isEmpty()) "" else ".$decimals", DecimalFormatSymbols.getInstance(locale))
        format.roundingMode = RoundingMode.HALF_UP
        val currency = if (currencyCode == tenant.currencyCode) currency() else currencyCode
        return listOf(format.format(value) + suffix, currency).filter { it.isNotEmpty() }.joinToString("\u00A0")
    }

    // French groups digits with a narrow no-break space (U+202F): a no-break space instead, which every font has. The
    // pattern's own spaces too, so an amount never wraps.
    private fun number(amount: Long, pattern: String): String =
        DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale)).format(amount)
            .replace('\u202F', '\u00A0')
            .replace(' ', '\u00A0')
}
