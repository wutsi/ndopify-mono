package com.wutsi.ndopify.npc.ui.mandate

/** The two terms the agent confirms when sending a mandate's contract. */
data class MandateContractForm(
    val durationMonths: String = "",
    val visitFee: String = "", // Strings, so a hand-edited value is a field error, not a 400
)
