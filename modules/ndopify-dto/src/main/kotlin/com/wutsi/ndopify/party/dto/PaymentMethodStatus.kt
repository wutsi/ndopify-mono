package com.wutsi.ndopify.party.dto

enum class PaymentMethodStatus {
    UNKNOWN,
    PENDING_VERIFICATION,
    VERIFIED,
    REJECTED,
    EXPIRED,
    DELETED,
}
