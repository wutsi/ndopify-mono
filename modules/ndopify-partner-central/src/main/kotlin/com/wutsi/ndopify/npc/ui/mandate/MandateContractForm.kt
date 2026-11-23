package com.wutsi.ndopify.npc.ui.mandate

/** The terms the agent confirms when sending a mandate's contract: its duration, a sale's commission, the visit fee. */
data class MandateContractForm(
    val durationMonths: String = "",
    val visitFee: String = "",
    val commissionRate: String = "", // Strings, so a hand-edited value is a field error, not a 400
)
