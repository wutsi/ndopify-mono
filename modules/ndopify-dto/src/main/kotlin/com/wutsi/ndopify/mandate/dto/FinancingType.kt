package com.wutsi.ndopify.mandate.dto

/**
 * How a buyer declares they will pay for a purchase: a declaration, not a proof — no document is collected.
 * Sent for a SALE search only; a rental has none.
 */
enum class FinancingType {
    UNKNOWN,
    CASH,
    BANK_LOAN,
    TRANSFER_FROM_ABROAD,
    UNDECIDED,
}
