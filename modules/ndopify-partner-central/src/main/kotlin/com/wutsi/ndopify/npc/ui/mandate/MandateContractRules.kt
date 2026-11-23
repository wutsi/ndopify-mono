package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.mandate.dto.RequestMandateApprovalRequest
import com.wutsi.ndopify.refdata.dto.Money

/**
 * The rules of the mandate contract page: pre-fill it from the current contract, check its fields and turn the
 * answers into the request's terms. Pure functions, re-checked by the server. A contract set through the API may have a
 * duration outside the page's 1–12: the page offers it and accepts it unchanged, never silently replacing it.
 *
 * A sale mandate (the page's [sale]) also states its commission in % of the sale price: a whole number from 1 to 20,
 * defaulting to [DEFAULT_COMMISSION]. A rental has none, whatever the hidden field holds.
 */
object MandateContractRules {
    const val DEFAULT_MONTHS = 6
    const val MIN_MONTHS = 1
    const val MAX_MONTHS = 12

    const val DEFAULT_COMMISSION = 5
    const val MIN_COMMISSION = 1
    const val MAX_COMMISSION = 20

    /** The contract's own terms when it has some, else 6 months, no fee and the default commission. */
    fun prefill(contract: Contract?): MandateContractForm = MandateContractForm(
        durationMonths = (contract?.durationMonths?.takeIf { it > 0 } ?: DEFAULT_MONTHS).toString(),
        visitFee = (contract?.visitFee?.amount?.takeIf { it > 0 } ?: 0).toString(),
        commissionRate = (contract?.commissionRate?.takeIf { it > 0 } ?: DEFAULT_COMMISSION).toString(),
    )

    /** The duration options: 1–12, plus the contract's own one when the API set it outside that range. */
    fun months(contract: Contract?): List<Int> {
        val months = (MIN_MONTHS..MAX_MONTHS).toMutableList()
        contract?.durationMonths?.takeIf { it > MAX_MONTHS }?.let { months.add(it) }
        return months
    }

    /**
     * The field errors, by form field name. An empty map when the form is valid. [currentDuration] is the contract's
     * own duration, which the agent may keep even outside [MIN_MONTHS]..[MAX_MONTHS]. A sale's commission is checked
     * only for a sale.
     */
    fun errors(form: MandateContractForm, currentDuration: Int? = null, sale: Boolean = false): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        val months = form.durationMonths.trim().toIntOrNull()
        if (months == null || (months !in MIN_MONTHS..MAX_MONTHS && months != currentDuration)) {
            errors["durationMonths"] = "Choisissez une durée de 1 à 12 mois."
        }
        if (sale && commission(form.commissionRate) == null) {
            errors["commissionRate"] = "Indiquez un pourcentage entier, de 1 à 20 (par exemple 5)."
        }
        if (amount(form.visitFee) == null) {
            errors["visitFee"] = "Indiquez un montant en chiffres, ou 0 pour aucun frais de visite."
        }
        return errors
    }

    /** The terms to send: the fee in the tenant's currency (a blank one being no fee), the rate of a sale only. */
    fun request(form: MandateContractForm, currency: String, sale: Boolean = false): RequestMandateApprovalRequest =
        RequestMandateApprovalRequest(
            durationMonths = form.durationMonths.trim().toIntOrNull() ?: DEFAULT_MONTHS,
            visitFee = Money(amount(form.visitFee) ?: 0, currency),
            commissionRate = if (sale) commission(form.commissionRate) else null,
        )

    // A whole percentage of the sale price, 1..20: spaces trimmed, a decimal or anything else is null
    private fun commission(value: String): Int? {
        val digits = value.replace(" ", "").replace("\u00A0", "").replace("\u202F", "").trim()
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) {
            return null
        }
        return digits.toIntOrNull()?.takeIf { it in MIN_COMMISSION..MAX_COMMISSION }
    }

    // "5 000" -> 5000; blank -> 0; anything that isn't a whole, positive amount -> null (a decimal stays one)
    private fun amount(value: String): Long? {
        val digits = value.replace(" ", "").replace("\u00A0", "").replace("\u202F", "").trim()
        if (digits.isEmpty()) {
            return 0
        }
        return digits.toLongOrNull()?.takeIf { it >= 0 }
    }
}
