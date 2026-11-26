package com.wutsi.ndopify.mandate.server.service.document

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A bank pre-approval (*accord de principe bancaire*): the bank, who it's for, how much, and until when.
 *
 * [issuedAt] is the issue date, [expiresAt] the validity date — the type requires one, an unreadable one is a
 * problem ([DocumentProblemCodes.EXPIRY_DATE_UNREADABLE]). The amount is a decimal and [currency] its own ISO 4217
 * code; the beneficiaries are names, the way a title's owners are.
 */
data class BankPreApprovalData(
    override val detectedType: String? = null,
    val bankName: String? = null,
    val beneficiaries: List<String>? = null,
    val amount: BigDecimal? = null,
    val currency: String? = null,
    val issueDate: String? = null,
    val validityDate: String? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : FinancialData() {
    override fun toFields(): Map<String, String?> = linkedMapOf(
        "bankName" to bankName,
        "beneficiaries" to beneficiaries?.joinToString(", "),
        "amount" to amount?.toPlainString(),
        "currency" to currency,
        "issueDate" to issueDate,
        "validityDate" to validityDate,
    )

    override fun issuedAt(): LocalDate? = date(issueDate)

    override fun expiresAt(): LocalDate? = date(validityDate)

    override fun sanitized(): BankPreApprovalData = copy(
        bankName = AccountNumberSanitizer.scrub(bankName),
        beneficiaries = beneficiaries?.map { name -> AccountNumberSanitizer.scrub(name)!! },
    )
}
