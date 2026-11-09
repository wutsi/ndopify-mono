package com.wutsi.ndopify.npc.ui.mandate

/** The closing wizard's counterpart step: a match id, or "outside" for a deal with nothing of the agent's. */
data class CounterpartForm(
    val counterpart: String? = null,
)

/** The closing wizard's price step: the final price as typed ("240 000"), shown again when invalid. */
data class PriceForm(
    val finalPrice: String? = null,
)
