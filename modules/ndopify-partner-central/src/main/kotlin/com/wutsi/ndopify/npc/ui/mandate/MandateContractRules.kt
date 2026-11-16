package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.mandate.dto.RequestMandateApprovalRequest
import com.wutsi.ndopify.refdata.dto.Money

/**
 * The rules of the mandate contract page: pre-fill it from the current contract, check its two fields and turn the
 * answers into the request's terms. Pure functions, re-checked by the server. A contract set through the API may have a
 * duration outside the page's 1–12: the page offers it and accepts it unchanged, never silently replacing it.
 */
object MandateContractRules {
    const val DEFAULT_MONTHS = 6
    const val MIN_MONTHS = 1
    const val MAX_MONTHS = 12

    /** The contract's own terms when it has some, else 6 months and no fee. */
    fun prefill(contract: Contract?): MandateContractForm = MandateContractForm(
        durationMonths = (contract?.durationMonths?.takeIf { it > 0 } ?: DEFAULT_MONTHS).toString(),
        visitFee = (contract?.visitFee?.amount?.takeIf { it > 0 } ?: 0).toString(),
    )

    /** The duration options: 1–12, plus the contract's own one when the API set it outside that range. */
    fun months(contract: Contract?): List<Int> {
        val months = (MIN_MONTHS..MAX_MONTHS).toMutableList()
        contract?.durationMonths?.takeIf { it > MAX_MONTHS }?.let { months.add(it) }
        return months
    }

    /**
     * The field errors, by form field name. An empty map when the form is valid. [currentDuration] is the contract's
     * own duration, which the agent may keep even outside [MIN_MONTHS]..[MAX_MONTHS].
     */
    fun errors(form: MandateContractForm, currentDuration: Int? = null): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        val months = form.durationMonths.trim().toIntOrNull()
        if (months == null || (months !in MIN_MONTHS..MAX_MONTHS && months != currentDuration)) {
            errors["durationMonths"] = "Choisissez une durée de 1 à 12 mois."
        }
        if (amount(form.visitFee) == null) {
            errors["visitFee"] = "Indiquez un montant en chiffres, ou 0 pour aucun frais de visite."
        }
        return errors
    }

    /** The terms to send: the fee in the tenant's currency, a blank one being no fee. */
    fun request(form: MandateContractForm, currency: String): RequestMandateApprovalRequest =
        RequestMandateApprovalRequest(
            durationMonths = form.durationMonths.trim().toIntOrNull() ?: DEFAULT_MONTHS,
            visitFee = Money(amount(form.visitFee) ?: 0, currency),
        )

    // "5 000" -> 5000; blank -> 0; anything that isn't a whole, positive amount -> null (a decimal stays one)
    private fun amount(value: String): Long? {
        val digits = value.replace(" ", "").replace("\u00A0", "").replace("\u202F", "").trim()
        if (digits.isEmpty()) {
            return 0
        }
        return digits.toLongOrNull()?.takeIf { it >= 0 }
    }
}
