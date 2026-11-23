package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.match.dto.CloseMatchRequest
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType

/**
 * The closing wizard's field rules: the final price as typed, and the requests the confirm step sends.
 * Price parsing is [FindPropertyRules.parseAmount]'s, narrowed so that a non-numeric answer ("240k") is rejected
 * rather than silently read as its digits.
 */
object MandateCloseRules {
    /** "240 000" or "240.000" → 240000. Blank, zero, negative or a non-numeric answer → null. */
    fun amount(value: String?): Long? {
        val text = value.orEmpty()
        val digits = text.filterNot { it.isWhitespace() || Character.isSpaceChar(it) || it == '.' }
        return digits
            .takeIf { it.isNotEmpty() && it.all { char -> char.isDigit() } }
            ?.let { FindPropertyRules.parseAmount(text) }
            ?.takeIf { it > 0 }
    }

    /** The deal with the chosen counterpart; the price is the one the price step validated. */
    fun closeMatch(draft: PendingMandateClose, currency: String, sale: Boolean): CloseMatchRequest =
        CloseMatchRequest(finalPrice = price(draft, currency, sale))

    /** The outside closure, when the deal has no counterpart of the agent's. */
    fun closeMandate(draft: PendingMandateClose, currency: String, sale: Boolean): CloseMandateRequest =
        CloseMandateRequest(finalPrice = price(draft, currency, sale))

    /** A sale price is a one-off amount, a rent is monthly: the server stores the amount only, the contract states it. */
    private fun price(draft: PendingMandateClose, currency: String, sale: Boolean) = Money(
        amount = draft.priceAmount ?: 0,
        currency = currency,
        periodicity = if (sale) PeriodicityType.NONE else PeriodicityType.MONTHLY,
    )
}
