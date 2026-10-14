package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.MovingPeriod

/** Step "budget". The amount is text: it may come with thousands separators ("150 000"). */
data class BudgetForm(
    val priceAmount: String? = null,
    val movingPeriod: MovingPeriod? = null,
)
