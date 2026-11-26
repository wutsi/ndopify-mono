package com.wutsi.ndopify.npc.ui.document

import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * How one value the AI read is shown to the agent, keyed by #330/#333's `DocumentData` property names: a date in the
 * tenant's `dateFormat` (year included), a pay month as a month, an amount short with the document's currency (title
 * #292), a masked account number, a grouped surface and a word for an amount's nature. Every other value reads as
 * sent, and so does one it can't parse (an older payload, a value the AI wrote in words): the page never fails, and
 * nothing is silently dropped. The document's `currency` is the amounts' unit, never a row of its own.
 */
object DocumentFieldValues {
    const val CURRENCY = "currency"

    /** The keys that are never a field row. */
    val HIDDEN = setOf(CURRENCY)

    private const val AMOUNT_SPACE = " m²"
    private val DATES = setOf("issueDate", "expiryDate", "validityDate", "statementDate", "dateOfBirth")
    private val AMOUNTS = setOf("amount", "grossSalary", "netSalary")
    private val AMOUNT_KINDS = mapOf("BALANCE" to "Solde", "CERTIFIED" to "Attesté")

    /** [value] as the agent reads it; [currency] is the document's own currency code, null or blank for the tenant's. */
    fun of(key: String, value: String, currency: String?, formatter: TenantFormatter): String = when {
        key in DATES -> date(value, formatter)
        key == "payMonth" -> month(value, formatter)
        key in AMOUNTS -> amount(value, currency, formatter)
        key == "accountLast4" -> if (value.matches(Regex("\\d{4}"))) "•••• $value" else value
        key == "surface" -> value.toLongOrNull()?.let { formatter.count(it) + AMOUNT_SPACE } ?: value
        key == "amountKind" -> AMOUNT_KINDS[value] ?: value
        else -> value
    }

    private fun date(value: String, formatter: TenantFormatter): String =
        try {
            formatter.date(LocalDate.parse(value))
        } catch (ex: DateTimeParseException) {
            value
        }

    private fun month(value: String, formatter: TenantFormatter): String =
        try {
            val label = formatter.date(YearMonth.parse(value).atDay(1), "MMMM yyyy")
            label.replaceFirstChar { it.uppercaseChar() }
        } catch (ex: DateTimeParseException) {
            value
        }

    private fun amount(value: String, currency: String?, formatter: TenantFormatter): String =
        try {
            val amount = BigDecimal(value).toLong()
            if (currency.isNullOrBlank()) formatter.compactMoney(amount) else formatter.compactMoney(amount, currency)
        } catch (ex: NumberFormatException) {
            value
        }
}
