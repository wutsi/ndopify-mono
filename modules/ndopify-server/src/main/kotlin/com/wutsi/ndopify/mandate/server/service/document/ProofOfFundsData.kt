package com.wutsi.ndopify.mandate.server.service.document

import java.math.BigDecimal
import java.time.LocalDate

/** Whether a proof of funds shows the account's balance (a statement) or an amount the bank certifies (an attestation). */
enum class AmountKind {
    BALANCE,
    CERTIFIED,
}

/**
 * A proof of funds (*relevé de compte* or *attestation de fonds*): the bank, the account holder, its balance or the
 * certified amount, and the account's last four digits — never the whole number.
 *
 * [AccountNumberSanitizer] masks the reading before it's stored: neither `accountLast4` nor any other text field may
 * hold the full account number or the IBAN, whatever the model returned. [issuedAt] is the statement or attestation
 * date; a proof of funds doesn't expire.
 */
data class ProofOfFundsData(
    override val detectedType: String? = null,
    val bankName: String? = null,
    val accountHolder: String? = null,
    val amount: BigDecimal? = null,
    val amountKind: AmountKind? = null,
    val currency: String? = null,
    val statementDate: String? = null,
    val accountLast4: String? = null,
    override val valid: Boolean = false,
    val invalidityReason: String? = null,
    override val confidence: Double? = null,
    override val fieldConfidences: Map<String, Double>? = null,
) : FinancialData() {
    override fun toFields(): Map<String, String?> = linkedMapOf(
        "bankName" to bankName,
        "accountHolder" to accountHolder,
        "amount" to amount?.toPlainString(),
        "amountKind" to amountKind?.name,
        "currency" to currency,
        "statementDate" to statementDate,
        "accountLast4" to accountLast4,
    )

    override fun issuedAt(): LocalDate? = date(statementDate)

    override fun sanitized(): ProofOfFundsData = copy(
        bankName = AccountNumberSanitizer.scrub(bankName),
        accountHolder = AccountNumberSanitizer.scrub(accountHolder),
        accountLast4 = AccountNumberSanitizer.last4(accountLast4),
    )
}
