package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.MandateCancellationCode

/** The mandate page's cancel form: the reason the mandate ends without a lease. */
data class MandateEndForm(
    val cancellationCode: MandateCancellationCode? = null,
    val cancellationReason: String? = null,
)
