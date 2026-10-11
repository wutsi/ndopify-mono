package com.wutsi.ndopify.npc.ui.join

/** Step "paiement". With [sameAsWhatsapp], the number is taken from the session, not from the page. */
data class PaymentForm(
    val mobileMoneyNumber: String = "",
    val sameAsWhatsapp: Boolean = false,
)
