package com.wutsi.ndopify.mandate.server.service.document

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A pay slip (*bulletin de paie*): who employs whom, for which month, and the salaries.
 *
 * `payMonth` is a year-month (`2026-10`), the field the client shows; [issuedAt] is its first day. A pay slip doesn't
 * expire, so [expiresAt] is always null. The salaries are decimals and [currency] their own ISO 4217 code, never a
 * formatted amount: the client formats them through its tenant's `TenantFormatter`.
 */
data class PaySlipData(
    override val detectedType: String? = null,
    val employerName: String? = null,
    val employeeName: String? = null,
    val payMonth: String? = null,
    val grossSalary: BigDecimal? = null,
    val netSalary: BigDecimal? = null,
    val currency: String? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : FinancialData() {
    override fun toFields(): Map<String, String?> = linkedMapOf(
        "employerName" to employerName,
        "employeeName" to employeeName,
        "payMonth" to payMonth,
        "grossSalary" to grossSalary?.toPlainString(),
        "netSalary" to netSalary?.toPlainString(),
        "currency" to currency,
    )

    override fun issuedAt(): LocalDate? = monthStart(payMonth)

    override fun sanitized(): PaySlipData = copy(
        employerName = AccountNumberSanitizer.scrub(employerName),
        employeeName = AccountNumberSanitizer.scrub(employeeName),
        payMonth = AccountNumberSanitizer.scrub(payMonth),
        currency = AccountNumberSanitizer.scrub(currency),
    )
}
