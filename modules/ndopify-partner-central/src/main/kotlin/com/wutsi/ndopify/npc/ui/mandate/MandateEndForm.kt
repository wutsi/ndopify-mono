package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.MandateCancellationCode

/** The mandate page's end form: closing (lease signed, at [finalPrice]) or cancelling (with a reason). */
data class MandateEndForm(
    /** As typed ("150 000"): shown again when invalid. */
    val finalPrice: String? = null,
    val cancellationCode: MandateCancellationCode? = null,
    val cancellationReason: String? = null,
)
